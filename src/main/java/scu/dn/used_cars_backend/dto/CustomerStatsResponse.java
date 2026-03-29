package scu.dn.used_cars_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Thống kê nhanh cho dashboard khách: đã lưu, lịch sắp tới; cọc/đơn Tier 4 tạm 0.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerStatsResponse {

	private long savedVehicles;
	private long upcomingBookings;
	private long activeDeposits;
	private long totalOrders;
}
