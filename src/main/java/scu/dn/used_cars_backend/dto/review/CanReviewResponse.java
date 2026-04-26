package scu.dn.used_cars_backend.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanReviewResponse {

	private boolean canReview;
	private String reason;
}
