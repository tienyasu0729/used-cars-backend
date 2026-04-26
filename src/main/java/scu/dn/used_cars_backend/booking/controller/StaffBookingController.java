package scu.dn.used_cars_backend.booking.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import scu.dn.used_cars_backend.booking.dto.AssignBookingStaffRequest;
import scu.dn.used_cars_backend.booking.dto.BookingResponse;
import scu.dn.used_cars_backend.booking.dto.ScheduleGroupResponse;
import scu.dn.used_cars_backend.booking.service.BookingService;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
public class StaffBookingController {

	private final BookingService bookingService;

	private static boolean isAdmin(Authentication authentication) {
		if (authentication == null) {
			return false;
		}
		for (GrantedAuthority a : authentication.getAuthorities()) {
			if ("ROLE_ADMIN".equals(a.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	@GetMapping("/bookings")
	public ResponseEntity<ApiResponse<List<BookingResponse>>> listStaffBookings(
			@RequestParam int branchId,
			@RequestParam(required = false) String status,
			@PageableDefault(size = 20) Pageable pageable) {
		Page<BookingResponse> page = bookingService.listStaffBookings(branchId, status, pageable);
		PageMetaDto meta = PageMetaDto.builder()
				.page(page.getNumber())
				.size(page.getSize())
				.totalElements(page.getTotalElements())
				.totalPages(page.getTotalPages())
				.build();
		return ResponseEntity.ok(ApiResponse.success(page.getContent(), meta));
	}

	@GetMapping("/schedule")
	public ResponseEntity<ApiResponse<List<ScheduleGroupResponse>>> schedule(
			@RequestParam int branchId,
			@RequestParam String date) {
		LocalDate d = LocalDate.parse(date.trim());
		List<ScheduleGroupResponse> data = bookingService.getStaffSchedule(branchId, d);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	/** Phân công nhân viên phụ trách (ưu tiên BranchManager; SALESSTAFF không gọi UI manager). */
	@PatchMapping("/bookings/{id}/assign")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
	public ResponseEntity<ApiResponse<BookingResponse>> assignStaff(@PathVariable long id,
			@RequestParam int branchId, @Valid @RequestBody AssignBookingStaffRequest body,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		BookingResponse updated = bookingService.assignStaff(id, body, branchId, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(updated));
	}
}
