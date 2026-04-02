package scu.dn.used_cars_backend.dto.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchSettingsResponse {

	private String name;
	private String address;
	private String phone;
	private String manager;
	/** Tóm tắt: ngày mở đầu tiên (T2 → … → CN) — tương thích client cũ. */
	private LocalTime openTime;
	private LocalTime closeTime;
	private List<Integer> workingDays;
	/** Giờ chi tiết từng thứ (0 = CN … 6 = T7). */
	private List<BranchDayScheduleDto> dailySchedules;
	/** URL ảnh showroom (tối đa 15 ở tầng request). */
	private List<String> showroomImageUrls;
}
