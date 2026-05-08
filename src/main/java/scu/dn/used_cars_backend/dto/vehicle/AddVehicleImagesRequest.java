package scu.dn.used_cars_backend.dto.vehicle;

// DTO dùng cho POST /manager/vehicles/{id}/images — thêm ảnh xe sau khi upload Cloudinary.

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddVehicleImagesRequest {

	@NotEmpty(message = "Danh sách ảnh không được để trống.")
	@Valid
	private List<VehicleImageWriteDto> images;
}
