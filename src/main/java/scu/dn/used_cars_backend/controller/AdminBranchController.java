package scu.dn.used_cars_backend.controller;

// API quản lý chi nhánh — chỉ ADMIN.

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
import scu.dn.used_cars_backend.dto.admin.AdminBranchListItemDto;
import scu.dn.used_cars_backend.dto.admin.CreateAdminBranchRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminBranchRequest;
import scu.dn.used_cars_backend.service.AdminBranchService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/branches")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBranchController {

	private final AdminBranchService adminBranchService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<AdminBranchListItemDto>>> list() {
		return ResponseEntity.ok(ApiResponse.success(adminBranchService.listBranches()));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<AdminBranchListItemDto>> create(@Valid @RequestBody CreateAdminBranchRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(adminBranchService.createBranch(body)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<AdminBranchListItemDto>> update(@PathVariable int id,
			@Valid @RequestBody UpdateAdminBranchRequest body) {
		return ResponseEntity.ok(ApiResponse.success(adminBranchService.updateBranch(id, body)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> delete(@PathVariable int id) {
		adminBranchService.softDeleteBranch(id);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}
}
