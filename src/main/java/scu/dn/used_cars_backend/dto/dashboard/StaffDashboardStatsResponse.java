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
	private long pendingBookings;
	private long pendingConsultations;
	private long pendingOrders;
	private long weeklyOrders;
	private long availableVehicles;
}
