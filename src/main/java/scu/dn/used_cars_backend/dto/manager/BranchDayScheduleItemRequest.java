package scu.dn.used_cars_backend.dto.manager;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class BranchDayScheduleItemRequest {

	@NotNull(message = "day_of_week không được null.")
	@Min(value = 0, message = "day_of_week từ 0 (CN) đến 6 (T7).")
	@Max(value = 6, message = "day_of_week từ 0 (CN) đến 6 (T7).")
	private Integer dayOfWeek;

	@NotNull(message = "Trạng thái đóng/mở không được null.")
	private Boolean closed;

	/** Bắt buộc khi mở cửa; khi đóng có thể gửi giá trị giữ chỗ. */
	private LocalTime openTime;

	private LocalTime closeTime;
}
