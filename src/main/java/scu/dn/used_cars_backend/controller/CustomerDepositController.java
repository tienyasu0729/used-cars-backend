package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.customer.CustomerDepositRowDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.CustomerDepositService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deposits")
@RequiredArgsConstructor
public class CustomerDepositController {

	private final CustomerDepositService customerDepositService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<CustomerDepositRowDto>>> list(Authentication auth) {
		long userId = AuthenticationDetailsUtils.requireUserId(auth);
		return ResponseEntity.ok(ApiResponse.success(customerDepositService.listForCustomer(userId)));
	}
}
