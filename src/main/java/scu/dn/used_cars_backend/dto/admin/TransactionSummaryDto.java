package scu.dn.used_cars_backend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryDto {

	private BigDecimal totalCompleted;
	private BigDecimal totalPending;
	private BigDecimal totalCancelled;
	private long countAll;
}
