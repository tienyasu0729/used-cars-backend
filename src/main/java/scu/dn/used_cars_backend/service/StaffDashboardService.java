package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.dto.dashboard.StaffDashboardStatsResponse;
import scu.dn.used_cars_backend.repository.ConsultationRepository;
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
	private final ConsultationRepository consultationRepository;

	@Transactional(readOnly = true)
	public StaffDashboardStatsResponse getStats(int branchId) {
		LocalDate today = LocalDate.now();
		long todayBookings = bookingRepository.countTodayAtBranchExcludingCancelled(branchId, today);
		// Phiếu tư vấn pending có xe thuộc chi nhánh — không tính phiếu không gắn xe (Sprint 9).
		long pendingConsultations = consultationRepository.countPendingByVehicleBranchId(branchId);
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
