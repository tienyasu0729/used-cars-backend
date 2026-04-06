package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.consultation.ConsultationListItemDto;
import scu.dn.used_cars_backend.dto.consultation.CreateConsultationRequest;
import scu.dn.used_cars_backend.dto.consultation.CreateConsultationResponse;
import scu.dn.used_cars_backend.dto.consultation.PatchConsultationStatusRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.security.JwtRoleNames;
import scu.dn.used_cars_backend.service.ConsultationService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/consultations")
@RequiredArgsConstructor
public class ConsultationController {

	private final ConsultationService consultationService;

	@PostMapping
	public ResponseEntity<ApiResponse<CreateConsultationResponse>> create(@Valid @RequestBody CreateConsultationRequest body,
			Authentication auth) {
		Long uid = AuthenticationDetailsUtils.userIdIfPresent(auth);
		CreateConsultationResponse r = consultationService.create(uid, body);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r));
	}

	@GetMapping("/{id}/mine")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<ApiResponse<ConsultationListItemDto>> getMine(@PathVariable long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		return ResponseEntity.ok(ApiResponse.success(consultationService.getForCustomer(uid, id)));
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<List<ConsultationListItemDto>>> list(Authentication auth,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String priority,
			@RequestParam(name = "has_vehicle", required = false) Boolean hasVehicle,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		long actorId = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		Page<ConsultationListItemDto> pg = consultationService.list(actorId, role, status, priority, hasVehicle, page,
				size);
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("page", pg.getNumber());
		meta.put("size", pg.getSize());
		meta.put("total", pg.getTotalElements());
		meta.put("totalPages", pg.getTotalPages());
		return ResponseEntity.ok(ApiResponse.success(pg.getContent(), meta));
	}

	@PatchMapping("/{id}/respond")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> respond(@PathVariable long id, Authentication auth) {
		long actorId = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		consultationService.respond(actorId, role, id);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> updateStatus(@PathVariable long id,
			@Valid @RequestBody PatchConsultationStatusRequest body, Authentication auth) {
		long actorId = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		consultationService.updateStatus(actorId, role, id, body);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}
}
