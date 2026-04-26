package scu.dn.used_cars_backend.controller;

// API quản lý user toàn hệ thống — chỉ ADMIN.

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import scu.dn.used_cars_backend.dto.admin.AdminResetPasswordResponse;
import scu.dn.used_cars_backend.dto.admin.AdminUserListItemDto;
import scu.dn.used_cars_backend.dto.admin.AdminUserStatusPatchRequest;
import scu.dn.used_cars_backend.dto.admin.CreateAdminUserRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminUserRequest;
import scu.dn.used_cars_backend.service.AdminUserService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

	private final AdminUserService adminUserService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<AdminUserListItemDto>>> list(
			@RequestParam(required = false) String role,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Page<AdminUserListItemDto> pg = adminUserService.listUsers(role, status, search, page, size);
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("page", pg.getNumber());
		meta.put("size", pg.getSize());
		meta.put("total", pg.getTotalElements());
		meta.put("totalPages", pg.getTotalPages());
		return ResponseEntity.ok(ApiResponse.success(pg.getContent(), meta));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, Long>>> create(@Valid @RequestBody CreateAdminUserRequest body) {
		long id = adminUserService.createUser(body);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of("id", id)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> update(@PathVariable long id,
			@Valid @RequestBody UpdateAdminUserRequest body) {
		adminUserService.updateUser(id, body);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> patchStatus(@PathVariable long id,
			@Valid @RequestBody AdminUserStatusPatchRequest body) {
		adminUserService.patchStatus(id, body);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> delete(@PathVariable long id) {
		adminUserService.softDeleteUser(id);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}

	@PostMapping("/{id}/reset-password")
	public ResponseEntity<ApiResponse<AdminResetPasswordResponse>> resetPassword(@PathVariable long id) {
		return ResponseEntity.ok(ApiResponse.success(adminUserService.resetPassword(id)));
	}
}
