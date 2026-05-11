package tech.ynfy.config.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tech.ynfy.frame.config.exception.BizException;
import tech.ynfy.frame.config.exception.RedisLockException;
import tech.ynfy.frame.module.Result;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * @author yyx
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	
	/**
	 * spring-boot-starter-validation
	 * 
	 * @param ex
	 * @return field validation error message
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public Result handleValidException(MethodArgumentNotValidException ex) {
		String msg = ex.getBindingResult()
					   .getFieldErrors()
					   .stream()
					   // key: msg
					   .map(e -> e.getField() + ": " + e.getDefaultMessage())
					   // 去重（可选）
					   .distinct()
					   // \n 分行
					   .collect(Collectors.joining("\n"));
		
		if (msg == null || msg.isBlank()) {
			msg = ex.getMessage();
		}
		return Result.error(msg);
	}
	
	/**
	 * base exception
	 */
	@ExceptionHandler(Exception.class)
	public Result baseException(Exception e) {
		log.error(e.getMessage(), e);
		return Result.error(String.format("系统异常-请联系管理员：%s", e.getMessage()));
	}
	
	/**
	 * business exception
	 */
	@ExceptionHandler(BizException.class)
	public Result customBizException(BizException e) {
		log.error(e.getMessage(), e);
		return Result.error(e.getCode(), String.format("%s", e.getMessage()));
	}
	
	@Value("${server.servlet.context-path}")
	private String servicePrefix;
	
	/**
	 * business exception
	 */
	@ExceptionHandler(RedisLockException.class)
	public Result customBizException(RedisLockException e) {
		log.error(e.getMessage(), e);
		return Result.error(e.getCode(), String.format("%s %s",servicePrefix, e.getMessage()));
	}
	
}
