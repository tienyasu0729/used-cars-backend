package scu.dn.used_cars_backend.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitReviewRequest {

	@NotNull
	@Min(1)
	@Max(5)
	private Integer rating;

	@Size(max = 2000)
	private String comment;

	private boolean anonymous = false;

	@NotNull
	private Long bookingId;
}
