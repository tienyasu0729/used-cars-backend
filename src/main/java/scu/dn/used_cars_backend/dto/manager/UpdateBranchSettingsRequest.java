package scu.dn.used_cars_backend.dto.manager;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UpdateBranchSettingsRequest {

	@NotBlank(message = "Tên chi nhánh không được để trống.")
	@Size(max = 200, message = "Tên tối đa 200 ký tự.")
	private String name;

	@NotBlank(message = "Địa chỉ không được để trống.")
	@Size(max = 500, message = "Địa chỉ tối đa 500 ký tự.")
	private String address;

	@Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
	private String phone;

	/** Đúng 7 phần tử, mỗi {@code day_of_week} 0–6 xuất hiện đúng một lần. */
	@NotNull(message = "Lịch theo ngày không được null.")
	@Size(min = 7, max = 7, message = "Phải gửi đủ 7 ngày trong tuần.")
	private List<@NotNull @Valid BranchDayScheduleItemRequest> dailySchedules;

	@Size(max = 15, message = "Tối đa 15 ảnh showroom.")
	private List<@Size(max = 2048, message = "Mỗi URL tối đa 2048 ký tự.") String> showroomImageUrls = new ArrayList<>();
}
