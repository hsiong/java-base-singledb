package tech.ynfy.frame.config.redislock;

import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tech.ynfy.frame.util.MD5Utils;
import tech.ynfy.frame.util.SpElUtil;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 RedisTemplate 的 分布式锁 + 限流 AOP
 */
@Aspect
@Component
@Slf4j
public class RedisLockAspect {
	
	private static final String LIMIT_PREFIX = "limit_";
	
	// 解锁脚本，只删除自己加的锁
	private static final String UNLOCK_LUA =
		"if redis.call('get', KEYS[1]) == ARGV[1] then " +
		"   return redis.call('del', KEYS[1]) " +
		"else " +
		"   return 0 " +
		"end";
	
	// 预编译好的脚本对象
	private static final RedisScript<Long> UNLOCK_SCRIPT;
	
	static {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setScriptText(UNLOCK_LUA);
		script.setResultType(Long.class);
		UNLOCK_SCRIPT = script;
	}
	
	@Autowired
	private RedisTemplate<String, Object> redisTemplate;
	
	@Autowired(required = false)
	private HttpServletRequest httpServletRequest;
	
	@Pointcut("@annotation(redissonLock)")
	public void redisLockPointcut(RedisLock redissonLock) {
	}
	
	@Around("redisLockPointcut(redissonLock)")
	public Object around(ProceedingJoinPoint joinPoint, RedisLock redissonLock) throws Throwable {
		
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		
		// 1. 构建锁 key
		String lockKey = buildLockKey(redissonLock, method, joinPoint.getArgs());
		
		// 2. 限流（按 lockKey 维度）
		long limitTime = redissonLock.limitTime();
		if (limitTime > 0) {
			String limitKey = LIMIT_PREFIX + lockKey;
			long limitSeconds = redissonLock.limitUnit().toSeconds(limitTime);
			
			Boolean ok = redisTemplate.opsForValue()
									  .setIfAbsent(limitKey, "1", Duration.ofSeconds(limitSeconds));
			// setIfAbsent 返回 false 表示 key 已存在 → 被限流
			if (Boolean.FALSE.equals(ok)) {
				if (redissonLock.throwException()) {
					throw buildLockLimitException(redissonLock);
				}
				return null;
			}
		}
		
		// 3. 分布式锁（SETNX + 过期时间）
		String lockValue = UUID.randomUUID().toString();  // 用于解锁校验
		long waitMillis = redissonLock.waitUnit().toMillis(redissonLock.waitTime());
		long leaseMillis = redissonLock.leaseTime() > 0
						   ? redissonLock.leaseUnit().toMillis(redissonLock.leaseTime())
						   : 30_000L; // 默认 30 秒租约，可以按需调整
		
		long deadline = System.currentTimeMillis() + Math.max(waitMillis, 0);
		boolean lockSuccess = false;
		
		try {
			if (waitMillis <= 0) {
				// 不等待，只尝试一次
				lockSuccess = trySetLockOnce(lockKey, lockValue, leaseMillis);
			} else {
				// 在 waitMillis 时间内自旋抢锁
				while (System.currentTimeMillis() < deadline) {
					lockSuccess = trySetLockOnce(lockKey, lockValue, leaseMillis);
					if (lockSuccess) {
						break;
					}
					Thread.sleep(50); // 稍微 sleep，避免狂刷 Redis
				}
			}
			
			if (!lockSuccess) {
				if (redissonLock.throwException()) {
					throw buildLockLimitException(redissonLock);
				}
				return null;
			}
			
			// 4. 执行业务逻辑
			return joinPoint.proceed();
			
		} catch (Exception e) {
			
			if (redissonLock.throwException()) {
				throw e;
			}
			return null;
			
		} finally {
			// 5. 解锁（只删除自己加的锁）
			if (lockSuccess) {
				try {
					redisTemplate.execute(
						UNLOCK_SCRIPT,
						Collections.singletonList(lockKey),
						lockValue
					);
				} catch (Exception ex) {
					log.warn("redis 分布式锁解锁异常, key:{}, err:{}",
							 lockKey, ex.getMessage(), ex);
				}
			}
		}
	}
	
	/**
	 * 尝试加锁一次
	 */
	private boolean trySetLockOnce(String lockKey, String lockValue, long leaseMillis) {
		try {
			Boolean ok = redisTemplate.opsForValue()
									  .setIfAbsent(lockKey, lockValue, Duration.ofMillis(leaseMillis));
			return Boolean.TRUE.equals(ok);
		} catch (Exception e) {
			log.error("尝试获取 redis 锁异常, key:{}, err:{}",
					  lockKey, e.getMessage(), e);
			throw e; 
		}
	}
	
	/**
	 * 构建锁 key
	 * 支持：
	 *  - prefix 为空时：类名.方法名
	 *  - key = "MD5"：对第一个参数做 MD5
	 *  - key 以 "#header." 开头：从 header 中取
	 *  - key 以 "#" 开头：SpEL，从方法参数取
	 *  - 其他：字面量
	 */
	private String buildLockKey(RedisLock redissonLock, Method method, Object[] args) {
		String prefix = StringUtils.isBlank(redissonLock.prefix())
						? (method.getDeclaringClass().getName() + "." + method.getName())
						: redissonLock.prefix();
		
		StringBuilder sb = new StringBuilder(prefix);
		String[] keys = redissonLock.key();
		
		if (keys.length > 0) {
			sb.append(":");
		}
		
		for (int i = 0; i < keys.length; i++) {
			String key = keys[i];
			
			if ("MD5".equalsIgnoreCase(key)) {
				if (args == null || args.length == 0 || args[0] == null) {
					throw new IllegalArgumentException("MD5校验必须需要第一个参数,并且不能为空！");
				}
				sb.append(MD5Utils.stringToMD5(JSON.toJSONString(args[0])));
				continue;
			}
			
			if (key.startsWith("#header.")) {
				if (httpServletRequest == null) {
					throw new IllegalStateException("HttpServletRequest 未注入，无法解析 #header.xxx");
				}
				String headerName = key.substring("#header.".length());
				sb.append(httpServletRequest.getHeader(headerName));
				continue;
			}
			
			if (key.startsWith("#")) {
				// 用你自己的 SpEL 工具
				Object val = SpElUtil.parseSpEl(method, args, key);
				sb.append(val);
				continue;
			}
			
			// 其他情况当作字面量
			sb.append(key);
			
			if (i < keys.length - 1) {
				sb.append(":");
			}
		}
		
		return sb.toString();
	}
	
	/**
	 * 统一构建“太频繁 / 加锁失败”异常
	 * 你可以换成你自己的业务异常类型
	 */
	private RuntimeException buildLockLimitException(RedisLock redissonLock) {
		String msg = StringUtils.defaultIfBlank(
			redissonLock.message(),
			"请求太频繁了，请稍后再试!"
		);
		return new RuntimeException(msg);
	}
}
