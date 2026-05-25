package scu.dn.used_cars_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "loan_config", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"term_months"})
})
@Getter
@Setter
public class LoanConfig extends BaseEntity {

	@Column(name = "term_months", nullable = false)
	private Integer termMonths;

	@Column(name = "interest_rate_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal interestRatePercent;

	@Column(name = "min_down_payment_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal minDownPaymentPercent;

	@Column(name = "active", nullable = false)
	private Boolean active = true;

	@Column(name = "description", length = 255)
	private String description;
}
