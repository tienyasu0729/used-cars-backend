package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.SystemAnnouncementDtos.AdminAnnouncementRowDto;
import scu.dn.used_cars_backend.dto.admin.SystemAnnouncementDtos.CreateSystemAnnouncementRequest;
import scu.dn.used_cars_backend.dto.admin.SystemAnnouncementDtos.UpdateSystemAnnouncementRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.AdminSystemAnnouncementService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemAnnouncementController {

	private final AdminSystemAnnouncementService adminSystemAnnouncementService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<AdminAnnouncementRowDto>>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var pg = adminSystemAnnouncementService.list(page, size);
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("page", pg.getNumber());
		meta.put("size", pg.getSize());
		meta.put("total", pg.getTotalElements());
		meta.put("totalPages", pg.getTotalPages());
		return ResponseEntity.ok(ApiResponse.success(pg.getContent(), meta));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, Integer>>> create(Authentication auth,
			@Valid @RequestBody CreateSystemAnnouncementRequest body) {
		long adminId = AuthenticationDetailsUtils.requireUserId(auth);
		Integer id = adminSystemAnnouncementService.create(adminId, body);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of("id", id)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> update(Authentication auth, @PathVariable int id,
			@Valid @RequestBody UpdateSystemAnnouncementRequest body) {
		long adminId = AuthenticationDetailsUtils.requireUserId(auth);
		adminSystemAnnouncementService.update(id, adminId, body);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> delete(@PathVariable int id) {
		adminSystemAnnouncementService.delete(id);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}

	@PostMapping("/{id}/publish")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> publish(@PathVariable int id) {
		adminSystemAnnouncementService.publish(id);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}
}
