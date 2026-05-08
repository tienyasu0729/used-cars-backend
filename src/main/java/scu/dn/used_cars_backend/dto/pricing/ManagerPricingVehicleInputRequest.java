package scu.dn.used_cars_backend.dto.pricing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ManagerPricingVehicleInputRequest {

	@NotBlank
	private String title;

	@NotNull
	@Min(1)
	private Integer categoryId;

	@NotNull
	@Min(1)
	private Integer subcategoryId;

	@NotNull
	@Min(1900)
	private Integer year;

	@NotNull
	@Min(0)
	private Integer mileage;

	@NotBlank
	private String fuel;

	@NotBlank
	private String transmission;

	private String bodyStyle;
	private String origin;
	private String description;
}
