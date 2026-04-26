package scu.dn.used_cars_backend.booking;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Hook cho giờ làm việc chi nhánh (Dev 1). Mặc định: không lọc — mọi slot template đều hiển thị.
 * Khi có bảng BranchWorkingHours, thay bằng bean triển khai thật.
 */
public interface BranchOpeningHoursProvider {

	/**
	 * @return true nếu {@code time} nằm trong khung làm việc của chi nhánh vào {@code date}.
	 */
	boolean isWithinWorkingHours(int branchId, LocalDate date, LocalTime time);
}
