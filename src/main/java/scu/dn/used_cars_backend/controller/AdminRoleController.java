package scu.dn.used_cars_backend.controller;

// API quản lý vai trò — chỉ ADMIN.

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.AdminRoleListItemDto;
import scu.dn.used_cars_backend.dto.admin.CreateAdminRoleRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminRoleRequest;
import scu.dn.used_cars_backend.service.AdminRoleService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleController {

	private final AdminRoleService adminRoleService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<AdminRoleListItemDto>>> list() {
		return ResponseEntity.ok(ApiResponse.success(adminRoleService.listRoles()));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<AdminRoleListItemDto>> create(@Valid @RequestBody CreateAdminRoleRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(adminRoleService.createRole(body)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<AdminRoleListItemDto>> update(@PathVariable int id,
			@Valid @RequestBody UpdateAdminRoleRequest body) {
		return ResponseEntity.ok(ApiResponse.success(adminRoleService.updateRole(id, body)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> delete(@PathVariable int id) {
		adminRoleService.deleteRole(id);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}
}
