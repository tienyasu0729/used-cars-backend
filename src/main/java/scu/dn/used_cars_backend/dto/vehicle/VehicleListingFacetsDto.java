package scu.dn.used_cars_backend.dto.vehicle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleListingFacetsDto {

	private List<Integer> categoryIds;
	private Map<Integer, List<Integer>> subcategoryIdsByCategory;
	private BigDecimal priceMin;
	private BigDecimal priceMax;
}
