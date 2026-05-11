package tech.ynfy.frame.config.exception;

/**
 * 〈〉
 *
 * @author Hsiong
 * @version 1.0.0
 * @since 11/19/25
 */
public class BizException extends RuntimeException {
	
	/**
	 * 默认业务异常错误码。
	 */
	public static final Integer BIZ_ERROR_CODE = 500;
	
	private final int code;
	
	public BizException(int code, String message) {
		super(message);
		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
}
