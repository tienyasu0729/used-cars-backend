package scu.dn.used_cars_backend.dto.vehicle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleSummaryDto {

	private Long id;
	private String listingId;
	private String title;
	private BigDecimal price;
	private Integer year;
	private Integer mileage;
	private String fuel;
	private String transmission;
	private Integer categoryId;
	private String categoryName;
	private Integer subcategoryId;
	private String subcategoryName;
	private Integer branchId;
	/** Tên chi nhánh — tiện cho bảng quản lý xe */
	private String branchName;
	private String status;
	/** Có cọc đang giữ slot mua — đồng bộ với kiểm tra đặt cọc */
	private boolean listingHoldActive;
	/** true = đã ẩn khỏi trang công khai (is_deleted), vẫn hiện trong quản lý chi nhánh */
	private boolean deleted;
	private String primaryImageUrl;

}
