package scu.dn.used_cars_backend.interaction.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ViewHistoryResponse {

	private final Long vehicleId;
	private final String listingId;
	private final String title;
	private final BigDecimal price;
	private final String primaryImageUrl;
}
