package scu.dn.used_cars_backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"),
	ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED"),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED"),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");

	private final HttpStatus httpStatus;
	private final String code;

	ErrorCode(HttpStatus httpStatus, String code) {
		this.httpStatus = httpStatus;
		this.code = code;
	}
}
