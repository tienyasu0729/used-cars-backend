package scu.dn.used_cars_backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerDashboardStatsResponse {

	/** TODO: Will be implemented in later sprint (Reporting / Analytics). Hiện {@link BigDecimal#ZERO}. */
	private BigDecimal monthlyRevenue;
	/** TODO: Will be implemented in later sprint (Reporting / Analytics). Hiện {@link BigDecimal#ZERO}. */
	private BigDecimal revenueChange;
	/** TODO: Will be implemented in later sprint (Reporting / Analytics). Hiện 0. */
	private long vehiclesSold;
	private long totalInventory;
	private long weeklyAppointments;
	/** TODO: Will be implemented in later sprint (Reporting / Analytics). Hiện rỗng. */
	private List<Object> topStaff;
}
