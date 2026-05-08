package scu.dn.used_cars_backend.interaction.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class SavedVehicleResponse {

	private final Long vehicleId;
	private final String listingId;
	private final String title;
	private final BigDecimal price;
	private final String status;
	private final String primaryImageUrl;
	private final Instant savedAt;
}
