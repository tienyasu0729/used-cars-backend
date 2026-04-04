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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
		String causeMsg = ex.getMostSpecificCause().getMessage();
		log.warn("Data integrity violation: {}", causeMsg);
		if (causeMsg != null && causeMsg.contains("UQ_Bookings_VehicleSlot")) {
			ApiErrorResponse body = ApiErrorResponse.builder()
					.timestamp(Instant.now())
					.status(ErrorCode.SLOT_FULLY_BOOKED.getHttpStatus().value())
					.errorCode(ErrorCode.SLOT_FULLY_BOOKED.getCode())
					.message("Xe này đã có lịch hẹn trong khung giờ này. Vui lòng chọn giờ khác.")
					.path(request.getRequestURI())
					.build();
			return ResponseEntity.status(ErrorCode.SLOT_FULLY_BOOKED.getHttpStatus()).body(body);
		}
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(ErrorCode.LISTING_ID_CONFLICT.getHttpStatus().value())
				.errorCode(ErrorCode.LISTING_ID_CONFLICT.getCode())
				.message("Dữ liệu trùng khóa hoặc vi phạm ràng buộc.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(ErrorCode.LISTING_ID_CONFLICT.getHttpStatus()).body(body);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex,
			HttpServletRequest request) {
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.NOT_FOUND.value())
				.errorCode(ErrorCode.RESOURCE_NOT_FOUND.getCode())
				.message("Không tìm thấy tài nguyên.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoHandler(NoHandlerFoundException ex,
			HttpServletRequest request) {
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.NOT_FOUND.value())
				.errorCode(ErrorCode.RESOURCE_NOT_FOUND.getCode())
				.message("Không tìm thấy API.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
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
			case INVALID_CURRENT_PASSWORD -> "Mật khẩu hiện tại không đúng.";
			case PASSWORD_TOO_SHORT -> "Mật khẩu từ 8 đến 100 ký tự.";
			case USER_NOT_FOUND -> "Không tìm thấy người dùng.";
			case ACCOUNT_SUSPENDED -> "Tài khoản bị khóa.";
			case PASSWORD_CHANGE_REQUIRED -> "Vui lòng đặt mật khẩu mới trước khi tiếp tục.";
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
			case TRANSFER_NOT_FOUND -> "Không tìm thấy yêu cầu điều chuyển.";
			case VEHICLE_NOT_IN_BRANCH -> "Xe không thuộc chi nhánh này.";
			case TRANSFER_ALREADY_EXISTS -> "Đã có yêu cầu điều chuyển cho xe này.";
			case INVALID_TRANSFER_STATUS -> "Trạng thái điều chuyển không hợp lệ.";
			case TRANSFER_ACCESS_DENIED -> "Không có quyền truy cập yêu cầu điều chuyển này.";
			case STAFF_NOT_FOUND -> "Không tìm thấy nhân viên.";
			case STAFF_EMAIL_EXISTS -> "Email đã được sử dụng.";
			case STAFF_PHONE_EXISTS -> "Số điện thoại đã được sử dụng.";
			case STAFF_NOT_IN_BRANCH -> "Nhân viên không thuộc chi nhánh của bạn.";
			case STAFF_PEER_EDIT_FORBIDDEN -> "Không thể chỉnh sửa nhân sự cùng vai trò với bạn.";
			case MEDIA_UPLOAD_NOT_CONFIGURED -> "Máy chủ chưa bật upload ảnh (Cloudinary).";
			case CLOUDINARY_URL_INVALID -> "URL ảnh Cloudinary không hợp lệ hoặc không khớp quyền upload.";
			case IMAGE_NOT_FOUND -> "Không tìm thấy ảnh xe.";
			case INVALID_VEHICLE_STATUS -> "Trạng thái xe không hợp lệ.";
			case INVALID_VEHICLE_LIST -> "Danh sách xe không hợp lệ.";
			case MAINTENANCE_NOT_FOUND -> "Không tìm thấy bản ghi bảo dưỡng.";
			case USER_EMAIL_EXISTS -> "Email đã được sử dụng.";
			case ROLE_NOT_FOUND -> "Không tìm thấy vai trò.";
			case ROLE_IN_USE -> "Vai trò đang được gán cho người dùng, không thể xóa.";
			case RESOURCE_NOT_FOUND -> "Không tìm thấy tài nguyên.";
			case ORDER_NOT_FOUND -> "Không tìm thấy đơn hàng.";
			case DEPOSIT_NOT_FOUND -> "Không tìm thấy khoản đặt cọc.";
			case VEHICLE_ALREADY_DEPOSITED -> "Xe đã có đặt cọc đang hiệu lực.";
			case DEPOSIT_CANNOT_CANCEL -> "Không thể hủy khoản đặt cọc này.";
			case DEPOSIT_CANNOT_CONFIRM -> "Không thể xác nhận khoản đặt cọc này.";
			case DEPOSIT_ACCESS_DENIED -> "Không có quyền truy cập khoản đặt cọc này.";
			case ORDER_INVALID_STATUS_TRANSITION -> "Chuyển trạng thái đơn hàng không hợp lệ.";
			case ORDER_CANNOT_CANCEL -> "Không thể hủy đơn hàng này.";
			case ORDER_ACCESS_DENIED -> "Không có quyền truy cập đơn hàng này.";
			case PAYMENT_EXCEEDS_REMAINING -> "Số tiền thanh toán vượt số còn lại.";
			case PAYMENT_FORBIDDEN -> "Không có quyền thanh toán đơn này.";
			case PAYMENT_AMOUNT_MISMATCH -> "Số tiền không khớp tiền cọc hoặc số còn lại.";
			case NOTIFICATION_NOT_FOUND -> "Không tìm thấy thông báo.";
			case ANNOUNCEMENT_NOT_FOUND -> "Không tìm thấy thông báo hệ thống.";
			case MAIL_NOT_CONFIGURED -> "Chưa cấu hình gửi email (SMTP).";
		};
	}
}
