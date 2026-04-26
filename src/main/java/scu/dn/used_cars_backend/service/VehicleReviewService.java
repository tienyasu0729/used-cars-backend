package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.entity.Booking;
import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.review.CanReviewResponse;
import scu.dn.used_cars_backend.dto.review.ReviewResponse;
import scu.dn.used_cars_backend.dto.review.ReviewSummaryResponse;
import scu.dn.used_cars_backend.dto.review.SubmitReviewRequest;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleReview;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.repository.VehicleReviewRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VehicleReviewService {

	private final VehicleReviewRepository reviewRepository;
	private final VehicleRepository vehicleRepository;
	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;

	@Transactional
	public ReviewResponse submitReview(Long vehicleId, Long userId, SubmitReviewRequest req) {
		Vehicle vehicle = vehicleRepository.findById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));

		if (reviewRepository.existsByVehicleIdAndReviewerId(vehicleId, userId)) {
			throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS, "Bạn đã đánh giá xe này rồi.");
		}

		Booking booking = bookingRepository.findById(req.getBookingId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

		if (!booking.getCustomerId().equals(userId) || !booking.getVehicle().getId().equals(vehicleId)) {
			throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED, "Lịch hẹn không thuộc về bạn hoặc xe này.");
		}

		if (!"Completed".equals(booking.getStatus())) {
			throw new BusinessException(ErrorCode.REVIEW_NOT_ELIGIBLE, "Chỉ đánh giá sau khi hoàn tất lái thử.");
		}

		User reviewer = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		VehicleReview review = new VehicleReview();
		review.setVehicle(vehicle);
		review.setReviewer(reviewer);
		review.setBooking(booking);
		review.setRating(req.getRating());
		review.setComment(req.getComment());
		review.setAnonymous(req.isAnonymous());
		review.setStatus("approved");

		reviewRepository.save(review);
		return toResponse(review);
	}

	@Transactional(readOnly = true)
	public Page<ReviewResponse> getApprovedReviews(Long vehicleId, Pageable pageable) {
		return reviewRepository.findByVehicleIdAndStatus(vehicleId, "approved", pageable)
				.map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public ReviewSummaryResponse getReviewSummary(Long vehicleId) {
		Double avg = reviewRepository.findAverageRatingByVehicleId(vehicleId);
		long total = reviewRepository.countApprovedByVehicleId(vehicleId);
		List<Object[]> dist = reviewRepository.findRatingDistribution(vehicleId);

		Map<Integer, Long> distribution = new HashMap<>();
		for (int i = 1; i <= 5; i++) {
			distribution.put(i, 0L);
		}
		for (Object[] row : dist) {
			distribution.put((Integer) row[0], (Long) row[1]);
		}

		return ReviewSummaryResponse.builder()
				.averageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : null)
				.totalReviews(total)
				.ratingDistribution(distribution)
				.build();
	}

	@Transactional(readOnly = true)
	public CanReviewResponse canReview(Long vehicleId, Long userId) {
		if (userId == null) {
			return CanReviewResponse.builder().canReview(false).reason("Vui lòng đăng nhập.").build();
		}
		if (reviewRepository.existsByVehicleIdAndReviewerId(vehicleId, userId)) {
			return CanReviewResponse.builder().canReview(false).reason("Bạn đã đánh giá xe này.").build();
		}
		return CanReviewResponse.builder().canReview(true).reason(null).build();
	}

	@Transactional(readOnly = true)
	public Page<ReviewResponse> getReviewsForAdmin(Long vehicleId, String status, Pageable pageable) {
		Specification<VehicleReview> spec = Specification.where(null);

		if (vehicleId != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("vehicle").get("id"), vehicleId));
		}
		if (status != null && !status.isBlank()) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
		}

		return reviewRepository.findAll(spec, pageable).map(this::toResponse);
	}

	@Transactional
	public ReviewResponse moderateReview(Long reviewId, String action) {
		VehicleReview review = reviewRepository.findById(reviewId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND, "Không tìm thấy đánh giá."));

		switch (action) {
			case "approve" -> review.setStatus("approved");
			case "reject" -> review.setStatus("rejected");
			case "hide" -> review.setStatus("hidden");
			default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Hành động không hợp lệ: " + action);
		}

		reviewRepository.save(review);
		return toResponse(review);
	}

	@Transactional
	public void deleteReview(Long reviewId) {
		VehicleReview review = reviewRepository.findById(reviewId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND, "Không tìm thấy đánh giá."));
		reviewRepository.delete(review);
	}

	private ReviewResponse toResponse(VehicleReview r) {
		String name = r.isAnonymous() ? "Ẩn danh" : (r.getReviewer() != null ? r.getReviewer().getName() : null);
		String avatar = r.isAnonymous() ? null : (r.getReviewer() != null ? r.getReviewer().getAvatarUrl() : null);

		return ReviewResponse.builder()
				.id(r.getId())
				.rating(r.getRating())
				.comment(r.getComment())
				.reviewerName(name)
				.reviewerAvatar(avatar)
				.anonymous(r.isAnonymous())
				.status(r.getStatus())
				.createdAt(r.getCreatedAt())
				.vehicleId(r.getVehicle() != null ? r.getVehicle().getId() : null)
				.vehicleTitle(r.getVehicle() != null ? r.getVehicle().getTitle() : null)
				.build();
	}
}
