package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.dto.admin.AdminBranchReportRowDto;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReportService {

	private final BranchRepository branchRepository;
	private final SalesOrderRepository salesOrderRepository;

	@Transactional(readOnly = true)
	public List<AdminBranchReportRowDto> branchOverviewRows() {
		List<Branch> branches = branchRepository.findAllByDeletedFalseOrderByIdAsc();
		List<AdminBranchReportRowDto> out = new ArrayList<>(branches.size());
		for (Branch b : branches) {
			Integer id = b.getId();
			long orders = salesOrderRepository.countOrdersExcludingCancelled(id);
			long vehiclesSold = salesOrderRepository.countSoldExcludingPendingAndCancelled(id);
			BigDecimal rev = salesOrderRepository.sumRevenueExcludingPendingAndCancelled(id);
			out.add(AdminBranchReportRowDto.builder()
					.branchName(b.getName())
					.revenue(rev != null ? rev.longValue() : 0L)
					.vehiclesSold(vehiclesSold)
					.orders(orders)
					.build());
		}
		return out;
	}
}
