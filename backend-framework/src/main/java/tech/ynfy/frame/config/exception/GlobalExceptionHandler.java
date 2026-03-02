package tech.ynfy.frame.config.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
	 * 基础异常
	 */
	@ExceptionHandler(Exception.class)
	public Result baseException(Exception e) {
		log.error(e.getMessage(), e);
		return Result.error(String.format("系统异常-请联系管理员：%s", e.getMessage()));
	}
}
