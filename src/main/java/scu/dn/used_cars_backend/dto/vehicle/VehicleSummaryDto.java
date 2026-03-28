package scu.dn.used_cars_backend.dto.vehicle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleSummaryDto {

	private Long id;
	private String listingId;
	private String title;
	private BigDecimal price;
	private Integer year;
	private Integer mileage;
	private String fuel;
	private String transmission;
	private Integer categoryId;
	private String categoryName;
	private Integer subcategoryId;
	private String subcategoryName;
	private Integer branchId;
	private String status;
	private String primaryImageUrl;

}
