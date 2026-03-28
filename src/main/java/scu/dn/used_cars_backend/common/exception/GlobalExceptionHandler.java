package scu.dn.used_cars_backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import scu.dn.used_cars_backend.common.error.ApiErrorResponse;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
		ErrorCode ec = ex.getErrorCode();
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(ec.getHttpStatus().value())
				.errorCode(ec.getCode())
				.message(ex.getMessage() != null && !ex.getMessage().equals(ec.name()) ? ex.getMessage()
						: defaultMessage(ec))
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(ec.getHttpStatus()).body(body);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		List<ApiErrorResponse.FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
				.map(this::toDetail)
				.collect(Collectors.toList());
		String msg = details.isEmpty() ? "Dữ liệu không hợp lệ." : details.get(0).getMessage();
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.BAD_REQUEST.value())
				.errorCode(ErrorCode.VALIDATION_FAILED.getCode())
				.message(msg)
				.path(request.getRequestURI())
				.errors(details)
				.build();
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest request) {
		log.debug("Authentication failed: {}", ex.getMessage());
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.UNAUTHORIZED.value())
				.errorCode(ErrorCode.UNAUTHORIZED.getCode())
				.message("Yêu cầu đăng nhập.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex,
			HttpServletRequest request) {
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.UNAUTHORIZED.value())
				.errorCode(ErrorCode.INVALID_CREDENTIALS.getCode())
				.message("Sai email hoặc mật khẩu.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(ErrorCode.LISTING_ID_CONFLICT.getHttpStatus().value())
				.errorCode(ErrorCode.LISTING_ID_CONFLICT.getCode())
				.message("Dữ liệu trùng khóa hoặc vi phạm ràng buộc.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(ErrorCode.LISTING_ID_CONFLICT.getHttpStatus()).body(body);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.FORBIDDEN.value())
				.errorCode(ErrorCode.FORBIDDEN.getCode())
				.message("Không có quyền truy cập.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleAny(Exception ex, HttpServletRequest request) {
		log.error("Unhandled error", ex);
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
				.errorCode(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
				.message("Lỗi hệ thống.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}

	private ApiErrorResponse.FieldErrorDetail toDetail(FieldError fe) {
		return new ApiErrorResponse.FieldErrorDetail(fe.getField(), fe.getDefaultMessage());
	}

	private String defaultMessage(ErrorCode ec) {
		return switch (ec) {
			case UNAUTHORIZED -> "Yêu cầu đăng nhập.";
			case FORBIDDEN -> "Không có quyền truy cập.";
			case INVALID_CREDENTIALS -> "Sai email hoặc mật khẩu.";
			case USER_NOT_FOUND -> "Không tìm thấy người dùng.";
			case ACCOUNT_SUSPENDED -> "Tài khoản bị khóa.";
			case VALIDATION_FAILED -> "Dữ liệu không hợp lệ.";
			case INTERNAL_SERVER_ERROR -> "Lỗi hệ thống.";
			case VEHICLE_NOT_FOUND -> "Không tìm thấy xe.";
			case BRAND_NOT_FOUND -> "Không tìm thấy hãng (category).";
			case MODEL_NOT_FOUND -> "Không tìm thấy dòng xe (subcategory).";
			case BRANCH_NOT_FOUND -> "Không tìm thấy chi nhánh.";
			case INVALID_PRICE -> "Giá không hợp lệ.";
			case INVALID_YEAR -> "Năm sản xuất không hợp lệ.";
			case LISTING_ID_CONFLICT -> "Mã tin trùng hoặc xung đột dữ liệu.";
			case VEHICLE_ALREADY_SAVED -> "Xe đã có trong danh sách đã lưu.";
			case VEHICLE_NOT_SAVED -> "Xe chưa được lưu.";
			case BOOKING_NOT_FOUND -> "Không tìm thấy lịch hẹn.";
			case SLOT_NOT_FOUND -> "Không tìm thấy khung giờ.";
			case SLOT_FULLY_BOOKED -> "Khung giờ đã đầy.";
			case VEHICLE_NOT_AVAILABLE -> "Xe không khả dụng để đặt lịch.";
			case BOOKING_CANNOT_CANCEL -> "Lịch hẹn không thể hủy.";
			case INVALID_STATUS_TRANSITION -> "Chuyển trạng thái không hợp lệ.";
			case BOOKING_ACCESS_DENIED -> "Không có quyền truy cập lịch hẹn này.";
		};
	}
}
