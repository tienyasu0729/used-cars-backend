package scu.dn.used_cars_backend.dto.installment;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SaveLoanConfigRequest {

	@NotNull(message = "termMonths is required")
	@Min(value = 6, message = "termMonths must be at least 6")
	@Max(value = 120, message = "termMonths must not exceed 120")
	private Integer termMonths;

	@NotNull(message = "interestRatePercent is required")
	@DecimalMin(value = "0.1", message = "interestRatePercent must be at least 0.1")
	@DecimalMax(value = "30.0", message = "interestRatePercent must not exceed 30")
	private BigDecimal interestRatePercent;

	@NotNull(message = "minDownPaymentPercent is required")
	@DecimalMin(value = "10.0", message = "minDownPaymentPercent must be at least 10")
	@DecimalMax(value = "90.0", message = "minDownPaymentPercent must not exceed 90")
	private BigDecimal minDownPaymentPercent;

	private Boolean active = true;

	private String description;
}
