package scu.dn.used_cars_backend.dto.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VehicleImageWriteDto {

	@NotBlank(message = "URL ảnh không được để trống.")
	private String url;

	@NotNull(message = "sortOrder bắt buộc.")
	private Integer sortOrder;

	@NotNull(message = "primary bắt buộc.")
	private Boolean primaryImage;

}
