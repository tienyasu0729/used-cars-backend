package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminDashboardStatsDto {
	long totalRevenue;
	long totalVehiclesSold;
	long totalInventory;
	long newCustomers;
	long activeBranches;
}
