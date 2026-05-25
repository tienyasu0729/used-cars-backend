package scu.dn.used_cars_backend.dto.installment;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanConfigDTO {
	private Long id;
	private Integer termMonths;
	private BigDecimal interestRatePercent;
	private BigDecimal minDownPaymentPercent;
	private Boolean active;
	private String description;
}
