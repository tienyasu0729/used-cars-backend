package scu.dn.used_cars_backend.common;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.Branch;

/** Kiểm tra chi nhánh còn mở cửa với khách hàng (status active, chưa xóa). */
public final class BranchPublicAccessSupport {

	private BranchPublicAccessSupport() {
	}

	public static boolean isPubliclyAccessible(Branch branch) {
		if (branch == null || branch.isDeleted()) {
			return false;
		}
		String status = branch.getStatus();
		if (status == null || status.isBlank()) {
			return true;
		}
		return "active".equalsIgnoreCase(status.trim());
	}

	public static void assertPubliclyAccessible(Branch branch) {
		if (!isPubliclyAccessible(branch)) {
			throw new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh.");
		}
	}
}
