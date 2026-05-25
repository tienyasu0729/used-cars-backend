package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.installment.LoanConfigDTO;
import scu.dn.used_cars_backend.dto.installment.SaveLoanConfigRequest;
import scu.dn.used_cars_backend.service.LoanConfigService;

import java.util.List;

@RestController
@RequestMapping("/api/loan-configs")
@RequiredArgsConstructor
public class LoanConfigController {

	private final LoanConfigService loanConfigService;

	@GetMapping("/public")
	public ResponseEntity<ApiResponse<List<LoanConfigDTO>>> getActiveConfigs() {
		List<LoanConfigDTO> configs = loanConfigService.getAllActiveConfigs();
		return ResponseEntity.ok(ApiResponse.success(configs));
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('Admin', 'BranchManager')")
	public ResponseEntity<ApiResponse<List<LoanConfigDTO>>> getAllConfigs() {
		List<LoanConfigDTO> configs = loanConfigService.getAllConfigs();
		return ResponseEntity.ok(ApiResponse.success(configs));
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('Admin', 'BranchManager')")
	public ResponseEntity<ApiResponse<LoanConfigDTO>> create(@Valid @RequestBody SaveLoanConfigRequest request) {
		LoanConfigDTO created = loanConfigService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyRole('Admin', 'BranchManager')")
	public ResponseEntity<ApiResponse<LoanConfigDTO>> update(@PathVariable Long id,
			@Valid @RequestBody SaveLoanConfigRequest request) {
		LoanConfigDTO updated = loanConfigService.update(id, request);
		return ResponseEntity.ok(ApiResponse.success(updated));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAnyRole('Admin', 'BranchManager')")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
		loanConfigService.delete(id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
