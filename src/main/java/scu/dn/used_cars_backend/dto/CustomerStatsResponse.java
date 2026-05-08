package scu.dn.used_cars_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thống kê nhanh cho dashboard khách.
 * <p>
 * {@code savedVehicles} / {@code upcomingBookings}: nguồn DB thật.
 * {@code activeDeposits} / {@code totalOrders}: cố định 0 cho tới khi Tier 4 (Orders/Deposits) — không phải đếm thật.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerStatsResponse {

	private long savedVehicles;
	private long upcomingBookings;
	// TODO: Will be implemented in later sprint (Reporting / Analytics / Deposits)
	private long activeDeposits;
	// TODO: Will be implemented in later sprint (Reporting / Analytics / Orders)
	private long totalOrders;
}
