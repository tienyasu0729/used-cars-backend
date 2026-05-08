package scu.dn.used_cars_backend.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

	private Long id;
	private int rating;
	private String comment;
	private String reviewerName;
	private String reviewerAvatar;
	private boolean anonymous;
	private String status;
	private Instant createdAt;
	private Long vehicleId;
	private String vehicleTitle;
}
