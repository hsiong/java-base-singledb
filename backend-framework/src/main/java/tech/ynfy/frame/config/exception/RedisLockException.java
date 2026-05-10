package tech.ynfy.frame.config.exception;

/**
 * 〈〉
 *
 * @author Hsiong
 * @version 1.0.0
 * @since 11/19/25
 */
public class RedisLockException extends RuntimeException {
	private final int code;
	
	public RedisLockException(int code, String message) {
		super(message);
		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
}
