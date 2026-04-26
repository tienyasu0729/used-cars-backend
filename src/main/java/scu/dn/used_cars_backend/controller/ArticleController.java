package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.article.ArticleCategoryResponse;
import scu.dn.used_cars_backend.dto.article.ArticleDetailResponse;
import scu.dn.used_cars_backend.dto.article.ArticleListItemResponse;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.service.ArticleCategoryService;
import scu.dn.used_cars_backend.service.ArticleService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

	private final ArticleService articleService;
	private final ArticleCategoryService categoryService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<ArticleListItemResponse>>> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String category,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "12") int size) {

		Page<ArticleListItemResponse> result = articleService.getPublishedArticles(
				keyword, category,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt")));

		PageMetaDto meta = PageMetaDto.builder()
				.page(result.getNumber())
				.size(result.getSize())
				.totalElements(result.getTotalElements())
				.totalPages(result.getTotalPages())
				.build();

		return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
	}

	@GetMapping("/{slug}")
	public ResponseEntity<ApiResponse<ArticleDetailResponse>> detail(@PathVariable String slug) {
		return ResponseEntity.ok(ApiResponse.success(articleService.getArticleBySlug(slug)));
	}

	@GetMapping("/categories")
	public ResponseEntity<ApiResponse<List<ArticleCategoryResponse>>> categories() {
		return ResponseEntity.ok(ApiResponse.success(categoryService.listActiveCategories()));
	}
}
