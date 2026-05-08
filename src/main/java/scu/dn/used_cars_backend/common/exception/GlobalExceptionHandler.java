package scu.dn.used_cars_backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
		log.warn("[BusinessException] {} {} -> {} {}: {}",
				request.getMethod(), request.getRequestURI(),
				ec.getHttpStatus().value(), ec.getCode(), ex.getMessage());
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(ec.getHttpStatus().value())
				.errorCode(ec.getCode())
				.message(ex.getMessage() != null && !ex.getMessage().equals(ec.name()) ? ex.getMessage() : defaultMessage(ec))
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(ec.getHttpStatus()).body(body);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<ApiErrorResponse.FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
				.map(this::toDetail)
				.collect(Collectors.toList());
		String msg = details.isEmpty() ? "Du lieu khong hop le." : details.get(0).getMessage();
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
				.message("Yeu cau dang nhap.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.UNAUTHORIZED.value())
				.errorCode(ErrorCode.INVALID_CREDENTIALS.getCode())
				.message("Sai email hoac mat khau.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
		String causeMsg = ex.getMostSpecificCause().getMessage();
		log.warn("[DataIntegrity] {} {} -> {}", request.getMethod(), request.getRequestURI(), causeMsg);
		if (causeMsg != null && causeMsg.contains("UQ_Bookings_VehicleSlot")) {
			ApiErrorResponse body = ApiErrorResponse.builder()
					.timestamp(Instant.now())
					.status(ErrorCode.VEHICLE_SLOT_TAKEN.getHttpStatus().value())
					.errorCode(ErrorCode.VEHICLE_SLOT_TAKEN.getCode())
					.message("Xe nay da co lich hen trong khung gio nay. Vui long chon gio khac.")
					.path(request.getRequestURI())
					.build();
			return ResponseEntity.status(ErrorCode.VEHICLE_SLOT_TAKEN.getHttpStatus()).body(body);
		}
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(ErrorCode.LISTING_ID_CONFLICT.getHttpStatus().value())
				.errorCode(ErrorCode.LISTING_ID_CONFLICT.getCode())
				.message("Du lieu trung khoa hoac vi pham rang buoc.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(ErrorCode.LISTING_ID_CONFLICT.getHttpStatus()).body(body);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.NOT_FOUND.value())
				.errorCode(ErrorCode.RESOURCE_NOT_FOUND.getCode())
				.message("Khong tim thay tai nguyen.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.NOT_FOUND.value())
				.errorCode(ErrorCode.RESOURCE_NOT_FOUND.getCode())
				.message("Khong tim thay API.")
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
				.message("Khong co quyen truy cap.")
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
				.message("Loi he thong.")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}

	private ApiErrorResponse.FieldErrorDetail toDetail(FieldError fe) {
		return new ApiErrorResponse.FieldErrorDetail(fe.getField(), fe.getDefaultMessage());
	}

	private String defaultMessage(ErrorCode ec) {
		return switch (ec) {
			case UNAUTHORIZED -> "Yeu cau dang nhap.";
			case RATE_LIMITED -> "Qua nhieu yeu cau, vui long thu lai sau.";
			case FORBIDDEN -> "Khong co quyen truy cap.";
			case INVALID_CREDENTIALS -> "Sai email hoac mat khau.";
			case INVALID_CURRENT_PASSWORD -> "Mat khau hien tai khong dung.";
			case PASSWORD_TOO_SHORT -> "Mat khau tu 8 den 100 ky tu.";
			case USER_NOT_FOUND -> "Khong tim thay nguoi dung.";
			case ACCOUNT_SUSPENDED -> "Tai khoan bi khoa.";
			case PASSWORD_CHANGE_REQUIRED -> "Vui long dat mat khau moi truoc khi tiep tuc.";
			case VALIDATION_FAILED -> "Du lieu khong hop le.";
			case INTERNAL_SERVER_ERROR -> "Loi he thong.";
			case VEHICLE_NOT_FOUND -> "Khong tim thay xe.";
			case BRAND_NOT_FOUND -> "Khong tim thay hang xe.";
			case MODEL_NOT_FOUND -> "Khong tim thay dong xe.";
			case BRANCH_NOT_FOUND -> "Khong tim thay chi nhanh.";
			case INVALID_PRICE -> "Gia khong hop le.";
			case INVALID_YEAR -> "Nam san xuat khong hop le.";
			case LISTING_ID_CONFLICT -> "Ma tin trung hoac xung dot du lieu.";
			case VEHICLE_ALREADY_SAVED -> "Xe da co trong danh sach da luu.";
			case VEHICLE_NOT_SAVED -> "Xe chua duoc luu.";
			case BOOKING_NOT_FOUND -> "Khong tim thay lich hen.";
			case SLOT_NOT_FOUND -> "Khong tim thay khung gio.";
			case SLOT_FULLY_BOOKED -> "Khung gio da day.";
			case VEHICLE_SLOT_TAKEN -> "Xe da co lich trong khung gio nay.";
			case VEHICLE_NOT_AVAILABLE -> "Xe khong kha dung de dat lich.";
			case BOOKING_CANNOT_CANCEL -> "Lich hen khong the huy.";
			case INVALID_STATUS_TRANSITION -> "Chuyen trang thai khong hop le.";
			case BOOKING_ACCESS_DENIED -> "Khong co quyen truy cap lich hen nay.";
			case TRANSFER_NOT_FOUND -> "Khong tim thay yeu cau dieu chuyen.";
			case VEHICLE_NOT_IN_BRANCH -> "Xe khong thuoc chi nhanh nay.";
			case TRANSFER_ALREADY_EXISTS -> "Da co yeu cau dieu chuyen cho xe nay.";
			case INVALID_TRANSFER_STATUS -> "Trang thai dieu chuyen khong hop le.";
			case TRANSFER_ACCESS_DENIED -> "Khong co quyen truy cap yeu cau dieu chuyen nay.";
			case USED_CAR_PURCHASE_REQUEST_NOT_FOUND -> "Khong tim thay ho so mua xe cu.";
			case USED_CAR_PURCHASE_REQUEST_ACCESS_DENIED -> "Khong co quyen thao tac ho so mua xe cu nay.";
			case USED_CAR_PURCHASE_REQUEST_INVALID_STATUS -> "Trang thai ho so mua xe cu khong hop le.";
			case STAFF_NOT_FOUND -> "Khong tim thay nhan vien.";
			case STAFF_EMAIL_EXISTS -> "Email da duoc su dung.";
			case STAFF_PHONE_EXISTS -> "So dien thoai da duoc su dung.";
			case STAFF_NOT_IN_BRANCH -> "Nhan vien khong thuoc chi nhanh cua ban.";
			case STAFF_PEER_EDIT_FORBIDDEN -> "Khong the chinh sua nhan su cung vai tro voi ban.";
			case MEDIA_UPLOAD_NOT_CONFIGURED -> "May chu chua bat upload anh Cloudinary.";
			case CLOUDINARY_URL_INVALID -> "URL Cloudinary khong hop le.";
			case IMAGE_NOT_FOUND -> "Khong tim thay anh xe.";
			case INVALID_VEHICLE_STATUS -> "Trang thai xe khong hop le.";
			case INVALID_VEHICLE_LIST -> "Danh sach xe khong hop le.";
			case MAINTENANCE_NOT_FOUND -> "Khong tim thay ban ghi bao duong.";
			case USER_EMAIL_EXISTS -> "Email da duoc su dung.";
			case ROLE_NOT_FOUND -> "Khong tim thay vai tro.";
			case ROLE_IN_USE -> "Vai tro dang duoc su dung.";
			case RESOURCE_NOT_FOUND -> "Khong tim thay tai nguyen.";
			case ORDER_NOT_FOUND -> "Khong tim thay don hang.";
			case DEPOSIT_NOT_FOUND -> "Khong tim thay khoan dat coc.";
			case VEHICLE_ALREADY_DEPOSITED -> "Xe da co dat coc dang hieu luc.";
			case DEPOSIT_CANNOT_CANCEL -> "Khong the huy khoan dat coc nay.";
			case DEPOSIT_CANNOT_CONFIRM -> "Khong the xac nhan khoan dat coc nay.";
			case DEPOSIT_ACCESS_DENIED -> "Khong co quyen truy cap khoan dat coc nay.";
			case ORDER_INVALID_STATUS_TRANSITION -> "Chuyen trang thai don hang khong hop le.";
			case ORDER_CANNOT_CANCEL -> "Khong the huy don hang nay.";
			case ORDER_ACCESS_DENIED -> "Khong co quyen truy cap don hang nay.";
			case VEHICLE_HAS_ACTIVE_ORDER -> "Xe da co don hang dang xu ly.";
			case DEPOSIT_OWNER_MISMATCH -> "Xe dang duoc khach khac dat coc.";
			case DEPOSIT_REQUIRED -> "Xe da co phieu coc can di kem khi tao don.";
			case DEPOSIT_ALREADY_CONVERTED -> "Phieu coc da duoc dung cho don khac.";
			case INVALID_VEHICLE_STATE_TRANSITION -> "Chuyen trang thai xe khong hop le.";
			case PAYMENT_EXCEEDS_REMAINING -> "So tien thanh toan vuot so con lai.";
			case PAYMENT_FORBIDDEN -> "Khong co quyen thanh toan don nay.";
			case PAYMENT_AMOUNT_MISMATCH -> "So tien thanh toan khong khop.";
			case NOTIFICATION_NOT_FOUND -> "Khong tim thay thong bao.";
			case ANNOUNCEMENT_NOT_FOUND -> "Khong tim thay thong bao he thong.";
			case MAIL_NOT_CONFIGURED -> "Chua cau hinh gui email.";
			case INVALID_RESET_TOKEN -> "Token dat lai mat khau khong hop le hoac da het han.";
			case GOOGLE_AUTH_FAILED -> "Xac thuc Google khong thanh cong.";
			case ARTICLE_NOT_FOUND -> "Khong tim thay bai viet.";
			case ARTICLE_CATEGORY_NOT_FOUND -> "Khong tim thay danh muc bai viet.";
			case ARTICLE_SLUG_CONFLICT -> "Slug bai viet da ton tai.";
			case CATEGORY_SLUG_CONFLICT -> "Slug danh muc da ton tai.";
			case CATEGORY_IN_USE -> "Danh muc dang duoc su dung.";
			case REVIEW_NOT_FOUND -> "Khong tim thay danh gia.";
			case REVIEW_ALREADY_EXISTS -> "Ban da danh gia xe nay roi.";
			case REVIEW_NOT_ELIGIBLE -> "Chi danh gia sau khi hoan tat lai thu.";
			case REVIEW_ACCESS_DENIED -> "Khong co quyen thao tac danh gia nay.";
			case CONTRACT_NOT_FOUND -> "Khong tim thay hop dong.";
			case CONTRACT_ALREADY_SIGNED -> "Hop dong da duoc ky.";
			case CONTRACT_EXPIRED -> "Hop dong da het han.";
			case DOCUMENT_SESSION_NOT_FOUND -> "Phien tai tai lieu khong ton tai.";
			case DOCUMENT_SESSION_EXPIRED -> "Phien tai tai lieu da het han.";
			case DOCUMENT_SESSION_INVALID_TOKEN -> "Ma phien tai tai lieu khong hop le.";
			case CONSULTATION_NOT_FOUND -> "Khong tim thay phieu tu van.";
			case CONSULTATION_ACCESS_DENIED -> "Khong co quyen thao tac phieu tu van nay.";
			case CHAT_NOT_FOUND -> "Khong tim thay hoi thoai.";
			case CHAT_ACCESS_DENIED -> "Khong co quyen truy cap cuoc tro chuyen nay.";
			case CUSTOMER_IDENTITY_AMBIGUOUS -> "Tim thay nhieu khach hang khop email hoac so dien thoai.";
			case BANK_CONNECTION_ERROR -> "Khong the ket noi toi dich vu tham dinh tin dung.";
			case BANK_API_ERROR -> "Loi tu dich vu tham dinh tin dung.";
		};
	}
}
