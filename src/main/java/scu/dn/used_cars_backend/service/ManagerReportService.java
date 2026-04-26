package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.admin.ManagerReportSalesByBrandDto;
import scu.dn.used_cars_backend.dto.admin.ManagerReportsResponseDto;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.service.support.CatalogSalesSupport;

import scu.dn.used_cars_backend.dto.admin.CatalogSalesModelRowDto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ManagerReportService {

	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

	private final VehicleRepository vehicleRepository;
	private final BranchRepository branchRepository;
	private final SalesOrderRepository salesOrderRepository;

	@Transactional(readOnly = true)
	public ManagerReportsResponseDto getReports(Integer branchIdFilter) {
		if (branchIdFilter != null) {
			branchRepository.findByIdAndDeletedFalse(branchIdFilter)
					.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		}
		List<Object[]> rows = vehicleRepository.countSoldByCategory(branchIdFilter);
		List<ManagerReportSalesByBrandDto> sales = new ArrayList<>();
		for (Object[] row : rows) {
			String name = row[1] != null ? String.valueOf(row[1]) : "";
			long cnt = row[2] instanceof Long l ? l : ((Number) row[2]).longValue();
			sales.add(ManagerReportSalesByBrandDto.builder().brand(name).count(cnt).build());
		}
		List<Long> monthly = new ArrayList<>(6);
		for (int i = 5; i >= 0; i--) {
			YearMonth ym = YearMonth.now(VN).minusMonths(i);
			Instant from = ym.atDay(1).atStartOfDay(VN).toInstant();
			Instant toEx = ym.plusMonths(1).atDay(1).atStartOfDay(VN).toInstant();
			BigDecimal sum = branchIdFilter == null
					? salesOrderRepository.sumCompletedAllBetween(from, toEx)
					: salesOrderRepository.sumCompletedInBranchBetween(branchIdFilter, from, toEx);
			monthly.add(sum != null ? sum.longValue() : 0L);
		}
		return ManagerReportsResponseDto.builder()
				.monthlyRevenue(monthly)
				.salesByBrand(sales)
				.topModels(CatalogSalesSupport.toModelRows(vehicleRepository.countSoldBySubcategory(branchIdFilter)))
				.staffPerformance(Collections.emptyList())
				.build();
	}

	// Xuất Excel báo cáo chi nhánh: 3 sheet — doanh thu theo tháng, bán theo hãng, top model
	@Transactional(readOnly = true)
	public byte[] exportReportsExcel(Integer branchIdFilter) {
		ManagerReportsResponseDto rpt = getReports(branchIdFilter);
		try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
			// Sheet 1: Doanh thu theo tháng
			org.apache.poi.ss.usermodel.Sheet s1 = wb.createSheet("Doanh thu");
			org.apache.poi.ss.usermodel.Row h1 = s1.createRow(0);
			h1.createCell(0).setCellValue("Tháng");
			h1.createCell(1).setCellValue("Doanh thu (VNĐ)");
			List<Long> monthly = rpt.getMonthlyRevenue();
			for (int i = 0; i < monthly.size(); i++) {
				YearMonth ym = YearMonth.now(VN).minusMonths(5 - i);
				org.apache.poi.ss.usermodel.Row r = s1.createRow(i + 1);
				r.createCell(0).setCellValue(ym.toString());
				r.createCell(1).setCellValue(monthly.get(i));
			}
			// Sheet 2: Bán theo hãng xe
			org.apache.poi.ss.usermodel.Sheet s2 = wb.createSheet("Bán theo hãng");
			org.apache.poi.ss.usermodel.Row h2 = s2.createRow(0);
			h2.createCell(0).setCellValue("Hãng xe");
			h2.createCell(1).setCellValue("Số lượng đã bán");
			int idx2 = 1;
			for (ManagerReportSalesByBrandDto b : rpt.getSalesByBrand()) {
				org.apache.poi.ss.usermodel.Row r = s2.createRow(idx2++);
				r.createCell(0).setCellValue(b.getBrand());
				r.createCell(1).setCellValue(b.getCount());
			}
			// Sheet 3: Top dòng xe bán chạy
			org.apache.poi.ss.usermodel.Sheet s3 = wb.createSheet("Top dòng xe");
			org.apache.poi.ss.usermodel.Row h3 = s3.createRow(0);
			h3.createCell(0).setCellValue("Dòng xe");
			h3.createCell(1).setCellValue("Hãng xe");
			h3.createCell(2).setCellValue("Số lượng");
			int idx3 = 1;
			for (CatalogSalesModelRowDto m : rpt.getTopModels()) {
				org.apache.poi.ss.usermodel.Row r = s3.createRow(idx3++);
				r.createCell(0).setCellValue(m.getModelName());
				r.createCell(1).setCellValue(m.getBrandName());
				r.createCell(2).setCellValue(m.getSoldCount());
			}
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			wb.write(out);
			return out.toByteArray();
		} catch (java.io.IOException e) {
			throw new RuntimeException("Lỗi tạo file Excel", e);
		}
	}
}
