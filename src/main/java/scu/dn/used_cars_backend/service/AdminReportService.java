package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.dto.admin.AdminBranchReportRowDto;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

	// Xuất Excel tổng quan chi nhánh toàn hệ thống
	@Transactional(readOnly = true)
	public byte[] exportExcel() {
		List<AdminBranchReportRowDto> rows = branchOverviewRows();
		try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
			org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Tổng quan chi nhánh");
			String[] headers = {"Chi nhánh", "Doanh thu (VNĐ)", "Xe đã bán", "Đơn hàng"};
			org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				headerRow.createCell(i).setCellValue(headers[i]);
			}
			int idx = 1;
			for (AdminBranchReportRowDto r : rows) {
				org.apache.poi.ss.usermodel.Row row = sheet.createRow(idx++);
				row.createCell(0).setCellValue(r.getBranchName() != null ? r.getBranchName() : "");
				row.createCell(1).setCellValue(r.getRevenue());
				row.createCell(2).setCellValue(r.getVehiclesSold());
				row.createCell(3).setCellValue(r.getOrders());
			}
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			wb.write(out);
			return out.toByteArray();
		} catch (java.io.IOException e) {
			throw new RuntimeException("Lỗi tạo file Excel", e);
		}
	}
}
