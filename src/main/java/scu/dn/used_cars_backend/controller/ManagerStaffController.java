package scu.dn.used_cars_backend.controller;

// API quản lý nhân viên chi nhánh — prefix /api/v1/manager/staff (Admin + BranchManager).

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.manager.CreateStaffRequest;
import scu.dn.used_cars_backend.dto.manager.RestoreStaffRequest;
import scu.dn.used_cars_backend.dto.manager.StaffAssignmentItemDto;
import scu.dn.used_cars_backend.dto.manager.StaffListItemDto;
import scu.dn.used_cars_backend.dto.manager.TransferStaffRequest;
import scu.dn.used_cars_backend.dto.manager.UpdateStaffRequest;
import scu.dn.used_cars_backend.dto.manager.UpdateStaffStatusRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.StaffService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/staff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
public class ManagerStaffController {

	private final StaffService staffService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<StaffListItemDto>>> list(
			@RequestParam(required = false) Integer branchId,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		List<StaffListItemDto> list = staffService.listStaff(branchId, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(list));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<StaffListItemDto>> create(@Valid @RequestBody CreateStaffRequest request,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		StaffListItemDto dto = staffService.createStaff(request, userId, admin);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dto));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<StaffListItemDto>> update(@PathVariable long id,
			@Valid @RequestBody UpdateStaffRequest request,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		StaffListItemDto dto = staffService.updateStaff(id, request, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(dto));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<ApiResponse<Void>> updateStatus(@PathVariable long id,
			@Valid @RequestBody UpdateStaffStatusRequest request,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		staffService.updateStaffStatus(id, request, userId, admin);
		return ResponseEntity.ok(ApiResponse.<Void>success(null));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long id, Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		staffService.softDeleteStaff(id, userId, admin);
		return ResponseEntity.ok(ApiResponse.<Void>success(null));
	}

	@PostMapping("/{id}/restore")
	public ResponseEntity<ApiResponse<StaffListItemDto>> restore(@PathVariable long id,
			@RequestBody(required = false) RestoreStaffRequest request,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		Integer branchId = request != null ? request.getBranchId() : null;
		StaffListItemDto dto = staffService.restoreStaff(id, branchId, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(dto));
	}

	@GetMapping("/{id}/assignments")
	public ResponseEntity<ApiResponse<List<StaffAssignmentItemDto>>> listAssignments(@PathVariable long id,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		List<StaffAssignmentItemDto> list = staffService.listAssignments(id, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(list));
	}

	@PostMapping("/{id}/assignments")
	public ResponseEntity<ApiResponse<StaffAssignmentItemDto>> transfer(@PathVariable long id,
			@Valid @RequestBody TransferStaffRequest request,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		StaffAssignmentItemDto dto = staffService.transferStaff(id, request, userId, admin);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dto));
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
