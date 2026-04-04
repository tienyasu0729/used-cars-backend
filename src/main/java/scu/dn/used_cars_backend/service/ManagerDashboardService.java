package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.dto.dashboard.ManagerDashboardStatsResponse;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class ManagerDashboardService {

	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

	private final BookingRepository bookingRepository;
	private final VehicleRepository vehicleRepository;
	private final SalesOrderRepository salesOrderRepository;

	@Transactional(readOnly = true)
	public ManagerDashboardStatsResponse getStats(int branchId) {
		LocalDate today = LocalDate.now();
		LocalDate weekEnd = today.plusDays(6);
		long totalInventory = vehicleRepository.countByBranch_IdAndDeletedFalse(branchId);
		long weeklyAppointments = bookingRepository.countBetweenDatesAtBranchExcludingCancelled(branchId, today, weekEnd);
		YearMonth ym = YearMonth.now(VN);
		LocalDate first = ym.atDay(1);
		LocalDate nextMonthFirst = ym.plusMonths(1).atDay(1);
		Instant fromM = first.atStartOfDay(VN).toInstant();
		Instant toMEx = nextMonthFirst.atStartOfDay(VN).toInstant();
		BigDecimal monthlyRev = salesOrderRepository.sumCompletedInBranchBetween(branchId, fromM, toMEx);
		long vehiclesSold = salesOrderRepository.countCompletedInBranchBetween(branchId, fromM, toMEx);
		return ManagerDashboardStatsResponse.builder()
				.monthlyRevenue(monthlyRev != null ? monthlyRev : BigDecimal.ZERO)
				.revenueChange(BigDecimal.ZERO)
				.vehiclesSold(vehiclesSold)
				.totalInventory(totalInventory)
				.weeklyAppointments(weeklyAppointments)
				.topStaff(Collections.emptyList())
				.build();
	}
}
