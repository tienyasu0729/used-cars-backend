package scu.dn.used_cars_backend.usedcarpurchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UsedCarPurchaseRequestActionRequest {

	@NotNull
	@DecimalMin(value = "0", inclusive = true)
	private BigDecimal approvedPurchasePrice;

	private String adminNote;
}
