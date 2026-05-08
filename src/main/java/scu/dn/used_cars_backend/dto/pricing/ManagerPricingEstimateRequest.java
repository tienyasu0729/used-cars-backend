package scu.dn.used_cars_backend.dto.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ManagerPricingEstimateRequest {

	@NotNull
	@Min(1)
	private Integer branchId;

	@Valid
	@NotNull
	private ManagerPricingVehicleInputRequest vehicleInput;

	@Valid
	@NotEmpty
	private List<ManagerPricingImageAssetRequest> imageAssets = new ArrayList<>();
}
