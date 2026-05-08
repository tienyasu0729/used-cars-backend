package scu.dn.used_cars_backend.usedcarpurchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import scu.dn.used_cars_backend.dto.pricing.ManagerPricingImageAssetRequest;
import scu.dn.used_cars_backend.dto.pricing.ManagerPricingVehicleInputRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class UsedCarPurchaseRequestCreateRequest {

	@NotNull
	@Min(1)
	private Integer branchId;

	@Valid
	@NotNull
	private ManagerPricingVehicleInputRequest vehicleInput;

	@Valid
	@NotEmpty
	private List<ManagerPricingImageAssetRequest> imageAssets = new ArrayList<>();

	@NotNull
	private Map<String, Object> valuationSnapshot;

	@NotNull
	@DecimalMin(value = "0", inclusive = true)
	private BigDecimal requestedPurchasePrice;

	private String managerNote;
}
