package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import jakarta.servlet.http.HttpServletRequest;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.web.HttpServletClientIp;
import scu.dn.used_cars_backend.dto.sales.CancelConfirmedDepositRequest;
import scu.dn.used_cars_backend.dto.sales.CancelDepositRequest;
import scu.dn.used_cars_backend.dto.sales.CreateDepositRequest;
import scu.dn.used_cars_backend.dto.sales.CreateDepositResponse;
import scu.dn.used_cars_backend.dto.sales.DepositListItemDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.security.JwtRoleNames;
import scu.dn.used_cars_backend.service.DepositService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deposits")
@RequiredArgsConstructor
public class DepositController {

	private final DepositService depositService;

	@PostMapping
	@PreAuthorize("hasAnyRole('CUSTOMER','SALESSTAFF','BRANCHMANAGER','ADMIN')")
	public ResponseEntity<ApiResponse<CreateDepositResponse>> create(@Valid @RequestBody CreateDepositRequest body,
			Authentication auth, HttpServletRequest request) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		CreateDepositResponse r = depositService.create(uid, role, body, HttpServletClientIp.resolve(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<DepositListItemDto>>> list(
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		List<DepositListItemDto> rows = depositService.page(uid, role, status, page, size);
		return ResponseEntity.ok(ApiResponse.success(rows, depositService.pageMeta(uid, role, status, page, size)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<DepositListItemDto>> get(@PathVariable long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		return ResponseEntity.ok(ApiResponse.success(depositService.getById(uid, role, id)));
	}

	@PatchMapping("/{id}/cancel")
	public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable long id,
			@RequestBody(required = false) CancelDepositRequest body, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		depositService.cancel(uid, role, id, body);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PostMapping("/{id}/cancel-confirmed")
	public ResponseEntity<ApiResponse<Void>> cancelConfirmed(@PathVariable long id,
			@Valid @RequestBody CancelConfirmedDepositRequest body, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		depositService.cancelConfirmedDeposit(uid, role, id, body.getReason());
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PatchMapping("/{id}/confirm")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<Void>> confirm(@PathVariable long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		depositService.confirm(uid, role, id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
