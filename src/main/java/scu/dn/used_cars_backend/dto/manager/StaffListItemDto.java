package scu.dn.used_cars_backend.dto.manager;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

// Một dòng trong danh sách nhân viên (Admin / BranchManager).
@Data
@Builder
public class StaffListItemDto {

	private Long id;
	private String name;
	private String email;
	private String phone;
	private String role;
	private Integer branchId;
	private String branchName;
	private String status;
	private Instant createdAt;
	/** true = đã gỡ khỏi nhân sự (soft delete), vẫn hiện trong danh sách chi nhánh. */
	private boolean deleted;
}
