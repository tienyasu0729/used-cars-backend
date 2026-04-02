package scu.dn.used_cars_backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffDashboardStatsResponse {

	private long todayBookings;
	/** TODO: Will be implemented in later sprint (Reporting / Analytics — module tư vấn). Hiện luôn 0. */
	private long pendingConsultations;
	/** TODO: Will be implemented in later sprint (Reporting / Analytics — đơn hàng tuần). Hiện luôn 0. */
	private long weeklyOrders;
	private long availableVehicles;
}
