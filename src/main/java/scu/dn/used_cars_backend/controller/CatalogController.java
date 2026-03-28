package scu.dn.used_cars_backend.controller;

// API đọc catalog: categories và subcategories (query categoryId).

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.catalog.CatalogCategoryDto;
import scu.dn.used_cars_backend.dto.catalog.CatalogSubcategoryDto;
import scu.dn.used_cars_backend.service.CatalogService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

	private final CatalogService catalogService;

	@GetMapping("/categories")
	public ResponseEntity<ApiResponse<List<CatalogCategoryDto>>> categories() {
		return ResponseEntity.ok(ApiResponse.success(catalogService.listCategories()));
	}

	@GetMapping("/subcategories")
	public ResponseEntity<ApiResponse<List<CatalogSubcategoryDto>>> subcategories(
			@RequestParam("categoryId") Integer categoryId) {
		return ResponseEntity.ok(ApiResponse.success(catalogService.listSubcategories(categoryId)));
	}

}
