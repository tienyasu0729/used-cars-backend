package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

/** Một dòng báo cáo tổng quan theo chi nhánh — khớp contract FE AdminReport. */
@Value
@Builder
public class AdminBranchReportRowDto {
	String branchName;
	long revenue;
	long vehiclesSold;
	long orders;
}
