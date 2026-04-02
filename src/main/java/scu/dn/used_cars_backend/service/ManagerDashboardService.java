package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.dto.dashboard.ManagerDashboardStatsResponse;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class ManagerDashboardService {

	private final BookingRepository bookingRepository;
	private final VehicleRepository vehicleRepository;

	@Transactional(readOnly = true)
	public ManagerDashboardStatsResponse getStats(int branchId) {
		LocalDate today = LocalDate.now();
		LocalDate weekEnd = today.plusDays(6);
		long totalInventory = vehicleRepository.countByBranch_IdAndDeletedFalse(branchId);
		long weeklyAppointments = bookingRepository.countBetweenDatesAtBranchExcludingCancelled(branchId, today, weekEnd);
		// TODO: Will be implemented in later sprint — monthlyRevenue, revenueChange, vehiclesSold, topStaff (placeholder, không mock)
		return ManagerDashboardStatsResponse.builder()
				.monthlyRevenue(BigDecimal.ZERO)
				.revenueChange(BigDecimal.ZERO)
				.vehiclesSold(0L)
				.totalInventory(totalInventory)
				.weeklyAppointments(weeklyAppointments)
				.topStaff(Collections.emptyList())
				.build();
	}
}
