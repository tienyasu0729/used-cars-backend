package scu.dn.used_cars_backend.dto.installment;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class InstallmentSubmitEligibilityResponse {
	private Long applicationId;
	private Long vehicleId;
	private Boolean hasValidDepositForVehicle;
	private Boolean depositProofUploaded;
	private BigDecimal appliedDepositAmount;
	private Boolean canSubmit;
	private String blockingReason;
}
