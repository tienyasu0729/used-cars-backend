package scu.dn.used_cars_backend.common.exception;

import lombok.Getter;
import scu.dn.used_cars_backend.common.error.ApiErrorResponse;

import java.util.List;

@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	private final List<ApiErrorResponse.FieldErrorDetail> errors;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
		this.errors = null;
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
		this.errors = null;
	}

	public BusinessException(ErrorCode errorCode, String message, List<ApiErrorResponse.FieldErrorDetail> errors) {
		super(message);
		this.errorCode = errorCode;
		this.errors = errors;
	}
}
