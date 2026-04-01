package scu.dn.used_cars_backend.dto.vehicle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDetailDto {

	private Long id;
	private String listingId;
	private String title;
	private BigDecimal price;
	private String description;
	private Integer year;
	private String fuel;
	private String transmission;
	private Integer mileage;
	private String bodyStyle;
	private String origin;
	private LocalDate postingDate;
	private String status;
	/** Đã ẩn khỏi tin đăng công khai (xóa mềm) — vẫn sửa được trong manager */
	private boolean deleted;
	private Integer categoryId;
	private String categoryName;
	private Integer subcategoryId;
	private String subcategoryName;
	private Integer branchId;
	private String branchName;
	private List<VehicleImageDto> images;

}
