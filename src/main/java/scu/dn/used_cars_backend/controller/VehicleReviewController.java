package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.review.CanReviewResponse;
import scu.dn.used_cars_backend.dto.review.ReviewResponse;
import scu.dn.used_cars_backend.dto.review.ReviewSummaryResponse;
import scu.dn.used_cars_backend.dto.review.SubmitReviewRequest;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.VehicleReviewService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/reviews")
@RequiredArgsConstructor
public class VehicleReviewController {

	private final VehicleReviewService reviewService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<ReviewResponse>>> list(
			@PathVariable Long vehicleId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {

		Page<ReviewResponse> result = reviewService.getApprovedReviews(
				vehicleId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

		PageMetaDto meta = PageMetaDto.builder()
				.page(result.getNumber())
				.size(result.getSize())
				.totalElements(result.getTotalElements())
				.totalPages(result.getTotalPages())
				.build();

		return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
	}

	@GetMapping("/summary")
	public ResponseEntity<ApiResponse<ReviewSummaryResponse>> summary(@PathVariable Long vehicleId) {
		return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewSummary(vehicleId)));
	}

	@GetMapping("/can-review")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<CanReviewResponse>> canReview(
			@PathVariable Long vehicleId,
			Authentication authentication) {
		Long userId = AuthenticationDetailsUtils.optionalUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(reviewService.canReview(vehicleId, userId)));
	}

	@PostMapping
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<ApiResponse<ReviewResponse>> submit(
			@PathVariable Long vehicleId,
			@Valid @RequestBody SubmitReviewRequest body,
			Authentication authentication) {
		Long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(reviewService.submitReview(vehicleId, userId, body)));
	}
}
