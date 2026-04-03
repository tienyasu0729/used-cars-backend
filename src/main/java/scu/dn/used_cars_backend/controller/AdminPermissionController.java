package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.AdminPermissionItemDto;
import scu.dn.used_cars_backend.entity.Permission;
import scu.dn.used_cars_backend.repository.PermissionRepository;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionController {

	private final PermissionRepository permissionRepository;

	@GetMapping
	public ResponseEntity<ApiResponse<List<AdminPermissionItemDto>>> list() {
		List<Permission> all = permissionRepository.findAll(Sort.by("module", "action"));
		List<AdminPermissionItemDto> out = all.stream()
				.map(p -> AdminPermissionItemDto.builder()
						.id(p.getId())
						.module(p.getModule())
						.action(p.getAction())
						.description(p.getDescription())
						.build())
				.collect(Collectors.toList());
		return ResponseEntity.ok(ApiResponse.success(out));
	}
}
