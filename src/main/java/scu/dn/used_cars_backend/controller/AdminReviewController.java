package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.review.ReviewResponse;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.service.VehicleReviewService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

	private final VehicleReviewService reviewService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<ReviewResponse>>> list(
			@RequestParam(required = false) Long vehicleId,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {

		Page<ReviewResponse> result = reviewService.getReviewsForAdmin(
				vehicleId, status,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

		PageMetaDto meta = PageMetaDto.builder()
				.page(result.getNumber())
				.size(result.getSize())
				.totalElements(result.getTotalElements())
				.totalPages(result.getTotalPages())
				.build();

		return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
	}

	@PatchMapping("/{id}/approve")
	public ResponseEntity<ApiResponse<ReviewResponse>> approve(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(reviewService.moderateReview(id, "approve")));
	}

	@PatchMapping("/{id}/reject")
	public ResponseEntity<ApiResponse<ReviewResponse>> reject(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(reviewService.moderateReview(id, "reject")));
	}

	@PatchMapping("/{id}/hide")
	public ResponseEntity<ApiResponse<ReviewResponse>> hide(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(reviewService.moderateReview(id, "hide")));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
		reviewService.deleteReview(id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
