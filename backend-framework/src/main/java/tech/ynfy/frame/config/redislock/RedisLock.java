package tech.ynfy.frame.config.redislock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 〈〉
 *
 * @author Hsiong
 * @version 1.0.0
 * @since 11/24/25
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RedisLock {
	/**
	 * 锁前缀，默认用 方法限定名
	 */
	String prefix() default "";
	
	/**
	 * 业务 key 片段，支持：
	 * - "MD5"：对第一个参数做 MD5
	 * - "#xxx"：SpEL
	 * - "#header.xxx"：从 header 取值
	 * - 其他字符串：当作字面量拼接
	 */
	String[] key() default {};
	
	/**
	 * 获取锁最大等待时间，小于等于 0 表示不等待，直接 tryLock()
	 */
	long waitTime() default 5L;
	
	TimeUnit waitUnit() default TimeUnit.SECONDS;
	
	/**
	 * 锁自动释放时间，小于等于 0 表示使用 Redisson 默认看门狗
	 */
	long leaseTime() default -1L;
	
	TimeUnit leaseUnit() default TimeUnit.SECONDS;
	
	/**
	 * 限流时间窗口，>0 表示开启限流（同一锁 key 在 limitTime 内只能成功一次）
	 */
	long limitTime() default 0L;
	
	TimeUnit limitUnit() default TimeUnit.SECONDS;
	
	/**
	 * true：抛异常；false：直接返回 null
	 */
	boolean throwException() default true;
	
	/**
	 * 错误提示信息
	 */
	String message() default "";
	
}
