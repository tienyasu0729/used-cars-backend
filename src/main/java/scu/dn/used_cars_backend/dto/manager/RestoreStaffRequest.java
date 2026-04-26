package scu.dn.used_cars_backend.dto.manager;

import lombok.Data;

/**
 * Khôi phục nhân viên đã gỡ (soft delete). Admin bắt buộc gửi {@code branchId} (chi nhánh khôi phục); quản lý chi nhánh
 * bỏ qua body — hệ thống dùng chi nhánh của người thao tác.
 */
@Data
public class RestoreStaffRequest {

	private Integer branchId;
}
