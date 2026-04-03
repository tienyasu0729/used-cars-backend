package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminBranchListItemDto {
	Integer id;
	String name;
	String address;
	String phone;
	String managerName;
	String status;
	long vehicleCount;
	long staffCount;
	/** Ảnh đại diện thẻ admin: URL đầu tiên từ `showroom_image_urls` (JSON), null nếu không có. */
	String imageUrl;
}
