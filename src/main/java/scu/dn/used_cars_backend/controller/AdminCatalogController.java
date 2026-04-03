package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.AdminCatalogBrandRowDto;
import scu.dn.used_cars_backend.dto.admin.AdminCatalogBrandsPageDto;
import scu.dn.used_cars_backend.dto.admin.AdminCatalogModelRowDto;
import scu.dn.used_cars_backend.dto.admin.AdminCatalogModelsPageDto;
import scu.dn.used_cars_backend.dto.admin.AdminCatalogTypedOptionDto;
import scu.dn.used_cars_backend.dto.admin.CreateAdminCatalogBrandRequest;
import scu.dn.used_cars_backend.dto.admin.CreateAdminCatalogModelRequest;
import scu.dn.used_cars_backend.dto.admin.CreateAdminCatalogOptionRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminCatalogBrandRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminCatalogModelRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminCatalogTypedOptionRequest;
import scu.dn.used_cars_backend.service.AdminCatalogService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

	private final AdminCatalogService adminCatalogService;

	@GetMapping("/brands")
	public ResponseEntity<ApiResponse<AdminCatalogBrandsPageDto>> brands(
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(ApiResponse.success(adminCatalogService.pageBrands(q, page, size)));
	}

	@PostMapping("/brands")
	public ResponseEntity<ApiResponse<AdminCatalogBrandRowDto>> createBrand(
			@Valid @RequestBody CreateAdminCatalogBrandRequest body) {
		return ResponseEntity.ok(ApiResponse.success(adminCatalogService.createBrand(body)));
	}

	@PutMapping("/brands/{id}")
	public ResponseEntity<ApiResponse<Void>> updateBrand(@PathVariable int id,
			@Valid @RequestBody UpdateAdminCatalogBrandRequest body) {
		adminCatalogService.updateBrand(id, body);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@GetMapping("/models")
	public ResponseEntity<ApiResponse<AdminCatalogModelsPageDto>> models(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) Integer categoryId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(ApiResponse.success(adminCatalogService.pageModels(q, categoryId, page, size)));
	}

	@PostMapping("/models")
	public ResponseEntity<ApiResponse<AdminCatalogModelRowDto>> createModel(
			@Valid @RequestBody CreateAdminCatalogModelRequest body) {
		return ResponseEntity.ok(ApiResponse.success(adminCatalogService.createModel(body)));
	}

	@PutMapping("/models/{id}")
	public ResponseEntity<ApiResponse<Void>> updateModel(@PathVariable int id,
			@Valid @RequestBody UpdateAdminCatalogModelRequest body) {
		adminCatalogService.updateModel(id, body);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@GetMapping("/fuel-types")
	public ResponseEntity<ApiResponse<List<AdminCatalogTypedOptionDto>>> fuelTypes() {
		return ResponseEntity.ok(ApiResponse.success(adminCatalogService.listFuelTypes()));
	}

	@PostMapping("/fuel-types")
	public ResponseEntity<ApiResponse<AdminCatalogTypedOptionDto>> createFuelType(
			@Valid @RequestBody CreateAdminCatalogOptionRequest body) {
		return ResponseEntity.ok(ApiResponse.success(adminCatalogService.createFuelType(body)));
	}

	@PutMapping("/fuel-types/{id}")
	public ResponseEntity<ApiResponse<Void>> updateFuelType(@PathVariable int id,
			@Valid @RequestBody UpdateAdminCatalogTypedOptionRequest body) {
		adminCatalogService.updateFuelType(id, body);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@GetMapping("/transmissions")
	public ResponseEntity<ApiResponse<List<AdminCatalogTypedOptionDto>>> transmissions() {
		return ResponseEntity.ok(ApiResponse.success(adminCatalogService.listTransmissions()));
	}

	@PostMapping("/transmissions")
	public ResponseEntity<ApiResponse<AdminCatalogTypedOptionDto>> createTransmission(
			@Valid @RequestBody CreateAdminCatalogOptionRequest body) {
		return ResponseEntity.ok(ApiResponse.success(adminCatalogService.createTransmission(body)));
	}

	@PutMapping("/transmissions/{id}")
	public ResponseEntity<ApiResponse<Void>> updateTransmission(@PathVariable int id,
			@Valid @RequestBody UpdateAdminCatalogTypedOptionRequest body) {
		adminCatalogService.updateTransmission(id, body);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
