package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.booking.service.BookingSlotConfigService;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.manager.BookingSlotSettingDto;
import scu.dn.used_cars_backend.dto.manager.BranchSettingsResponse;
import scu.dn.used_cars_backend.dto.manager.SimpleMessageResponse;
import scu.dn.used_cars_backend.dto.manager.UpdateBookingSlotsRequest;
import scu.dn.used_cars_backend.dto.manager.UpdateBranchSettingsRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.BranchService;
import scu.dn.used_cars_backend.service.StaffService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
public class ManagerSettingsController {

	private final BranchService branchService;
	private final BookingSlotConfigService bookingSlotConfigService;
	private final StaffService staffService;

	@GetMapping
	public ResponseEntity<ApiResponse<BranchSettingsResponse>> getSettings(
			@RequestParam(required = false) Long branchId,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		int resolved = staffService.resolveBranchIdForAdminOrBranchStaff(branchId, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(branchService.getBranchSettings(resolved)));
	}

	@PutMapping
	public ResponseEntity<ApiResponse<SimpleMessageResponse>> updateSettings(
			@RequestParam(required = false) Long branchId,
			@Valid @RequestBody UpdateBranchSettingsRequest request,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		int resolved = staffService.resolveBranchIdForAdminOrBranchStaff(branchId, userId, admin);
		branchService.updateBranchSettings(resolved, request);
		return ResponseEntity.ok(ApiResponse.success(SimpleMessageResponse.builder()
				.message("Cập nhật cài đặt chi nhánh thành công.")
				.build()));
	}

	@GetMapping("/booking-slots")
	public ResponseEntity<ApiResponse<List<BookingSlotSettingDto>>> listBookingSlots(
			@RequestParam(required = false) Long branchId,
			@RequestParam(required = false) Boolean activeOnly,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		int resolved = staffService.resolveBranchIdForAdminOrBranchStaff(branchId, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(bookingSlotConfigService.listSlotsForBranch(resolved, activeOnly)));
	}

	@PutMapping("/booking-slots")
	public ResponseEntity<ApiResponse<SimpleMessageResponse>> updateBookingSlots(
			@RequestParam(required = false) Long branchId,
			@Valid @RequestBody UpdateBookingSlotsRequest request,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		int resolved = staffService.resolveBranchIdForAdminOrBranchStaff(branchId, userId, admin);
		bookingSlotConfigService.updateSlotsForBranch(resolved, request);
		return ResponseEntity.ok(ApiResponse.success(SimpleMessageResponse.builder()
				.message("Cập nhật khung giờ đặt lịch thành công.")
				.build()));
	}

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
}
