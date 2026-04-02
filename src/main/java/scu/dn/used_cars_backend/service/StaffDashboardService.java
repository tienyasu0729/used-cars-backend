package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.dto.dashboard.StaffDashboardStatsResponse;
import scu.dn.used_cars_backend.entity.VehicleStatus;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
public class StaffDashboardService {

	private final BookingRepository bookingRepository;
	private final VehicleRepository vehicleRepository;
	private final SalesOrderRepository salesOrderRepository;

	@Transactional(readOnly = true)
	public StaffDashboardStatsResponse getStats(int branchId) {
		LocalDate today = LocalDate.now();
		LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
		long todayBookings = bookingRepository.countTodayAtBranchExcludingCancelled(branchId, today);
		long pendingConsultations = bookingRepository.countPendingAtBranch(branchId);
		long weeklyOrders = salesOrderRepository.countCreatedDateBetweenAtBranchExcludingCancelled(branchId, weekStart,
				weekEnd);
		long availableVehicles = vehicleRepository.countByBranchIdAndDeletedFalseAndStatus(branchId,
				VehicleStatus.AVAILABLE.getDbValue());
		return StaffDashboardStatsResponse.builder()
				.todayBookings(todayBookings)
				.pendingConsultations(pendingConsultations)
				.weeklyOrders(weeklyOrders)
				.availableVehicles(availableVehicles)
				.build();
	}
}
