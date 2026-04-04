package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.dto.dashboard.StaffDashboardStatsResponse;
import scu.dn.used_cars_backend.entity.VehicleStatus;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class StaffDashboardService {

	private final BookingRepository bookingRepository;
	private final VehicleRepository vehicleRepository;
	private final SalesOrderRepository salesOrderRepository;

	@Transactional(readOnly = true)
	public StaffDashboardStatsResponse getStats(int branchId) {
		LocalDate today = LocalDate.now();
		long todayBookings = bookingRepository.countTodayAtBranchExcludingCancelled(branchId, today);
		long pendingConsultations = bookingRepository.countPendingAtBranch(branchId);
		Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
		long weeklyOrders = salesOrderRepository.countSinceAtBranch(branchId, weekAgo);
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
