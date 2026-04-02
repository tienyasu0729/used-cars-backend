package scu.dn.used_cars_backend.dto.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Dữ liệu chi nhánh trả về client công khai (không lộ manager nội bộ). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchPublicDto {

	private Integer id;
	private String name;
	private String address;
	private String phone;
	private BigDecimal lat;
	private BigDecimal lng;

	/** Ảnh showroom (URL đã lưu từ cài đặt chi nhánh). */
	@Builder.Default
	private List<String> showroomImageUrls = new ArrayList<>();

	/** Giờ theo từng thứ (đủ 7 phần tử). */
	@Builder.Default
	private List<BranchPublicScheduleDto> workingHours = new ArrayList<>();

}
