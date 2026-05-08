package scu.dn.used_cars_backend.booking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scu.dn.used_cars_backend.booking.dto.BookingResponse;
import scu.dn.used_cars_backend.booking.dto.CreateManagerBookingRequest;
import scu.dn.used_cars_backend.booking.service.BookingService;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;

@RestController
@RequestMapping("/api/v1/manager/bookings")
@RequiredArgsConstructor
public class ManagerBookingController {

	private final BookingService bookingService;

	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<BookingResponse>> create(@Valid @RequestBody CreateManagerBookingRequest request,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		BookingResponse created = bookingService.createManagerBooking(request, userId);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
	}

	private static long requireUserId(Authentication authentication) {
		if (authentication == null || authentication.getDetails() == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		if (!(authentication.getDetails() instanceof Long userId)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		return userId;
	}
}
