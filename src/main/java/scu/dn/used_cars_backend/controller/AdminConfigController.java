package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.AdminConfigEntryDto;
import scu.dn.used_cars_backend.dto.admin.AdminConfigUpsertItemDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.AdminConfigService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminConfigController {

	private final AdminConfigService adminConfigService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<AdminConfigEntryDto>>> list() {
		return ResponseEntity.ok(ApiResponse.success(adminConfigService.listAll()));
	}

	@PutMapping
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> upsert(@Valid @RequestBody List<AdminConfigUpsertItemDto> body,
			Authentication authentication) {
		long uid = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(adminConfigService.upsert(body, uid)));
	}
}
