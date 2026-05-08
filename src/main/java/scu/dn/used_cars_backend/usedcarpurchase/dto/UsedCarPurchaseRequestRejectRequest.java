package scu.dn.used_cars_backend.usedcarpurchase.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UsedCarPurchaseRequestRejectRequest {

	@NotBlank
	private String adminNote;
}
