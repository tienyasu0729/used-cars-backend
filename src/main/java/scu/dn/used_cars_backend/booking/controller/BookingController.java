package scu.dn.used_cars_backend.booking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.booking.dto.AvailableSlotResponse;
import scu.dn.used_cars_backend.booking.dto.BookingResponse;
import scu.dn.used_cars_backend.booking.dto.ConfirmBookingRequest;
import scu.dn.used_cars_backend.booking.dto.CreateBookingRequest;
import scu.dn.used_cars_backend.booking.dto.RescheduleRequest;
import scu.dn.used_cars_backend.booking.service.BookingService;
import scu.dn.used_cars_backend.booking.service.SlotAvailabilityService;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

	private final BookingService bookingService;
	private final SlotAvailabilityService slotAvailabilityService;

	@GetMapping("/available-slots")
	public ResponseEntity<ApiResponse<List<AvailableSlotResponse>>> availableSlots(
			@RequestParam int branchId,
			@RequestParam String date) {
		LocalDate d = LocalDate.parse(date.trim());
		List<AvailableSlotResponse> data = slotAvailabilityService.getAvailableSlots(branchId, d);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@PostMapping
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<ApiResponse<BookingResponse>> create(@Valid @RequestBody CreateBookingRequest request,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		BookingResponse created = bookingService.createBooking(request, userId);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
	}

	@GetMapping
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<ApiResponse<List<BookingResponse>>> myList(
			@RequestParam(required = false) String status,
			@PageableDefault(size = 20) Pageable pageable,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		Page<BookingResponse> page = bookingService.listMyBookings(userId, status, pageable);
		PageMetaDto meta = PageMetaDto.builder()
				.page(page.getNumber())
				.size(page.getSize())
				.totalElements(page.getTotalElements())
				.totalPages(page.getTotalPages())
				.build();
		return ResponseEntity.ok(ApiResponse.success(page.getContent(), meta));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<ApiResponse<BookingResponse>> myDetail(@PathVariable long id,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(bookingService.getBookingForCustomer(id, userId)));
	}

	@PatchMapping("/{id}/cancel")
	@PreAuthorize("hasAnyRole('CUSTOMER','ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<BookingResponse>> cancel(@PathVariable long id,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		boolean staffActor = isStaffOrAbove(authentication);
		return ResponseEntity.ok(ApiResponse.success(bookingService.cancelBooking(id, userId, staffActor)));
	}

	@PatchMapping("/{id}/confirm")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<BookingResponse>> confirm(@PathVariable long id,
			@RequestBody(required = false) ConfirmBookingRequest body,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(bookingService.confirmBooking(id, userId, body)));
	}

	@PatchMapping("/{id}/reschedule")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<BookingResponse>> reschedule(@PathVariable long id,
			@Valid @RequestBody RescheduleRequest request,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(bookingService.rescheduleBooking(id, userId, request)));
	}

	@PatchMapping("/{id}/complete")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<BookingResponse>> complete(@PathVariable long id,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(bookingService.completeBooking(id, userId)));
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

	private static boolean isStaffOrAbove(Authentication authentication) {
		for (GrantedAuthority a : authentication.getAuthorities()) {
			String r = a.getAuthority();
			if ("ROLE_ADMIN".equals(r) || "ROLE_BRANCHMANAGER".equals(r) || "ROLE_SALESSTAFF".equals(r)) {
				return true;
			}
		}
		return false;
	}
}
