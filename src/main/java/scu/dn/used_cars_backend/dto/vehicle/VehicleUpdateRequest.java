package scu.dn.used_cars_backend.dto.vehicle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class VehicleUpdateRequest {

	@NotNull(message = "categoryId (hãng) bắt buộc.")
	private Integer categoryId;

	@NotNull(message = "subcategoryId (dòng xe) bắt buộc.")
	private Integer subcategoryId;

	@NotNull(message = "branchId bắt buộc.")
	private Integer branchId;

	@NotBlank(message = "Tiêu đề không được để trống.")
	private String title;

	@NotNull(message = "Giá bắt buộc.")
	@DecimalMin(value = "0", message = "Giá phải >= 0.")
	private BigDecimal price;

	@NotNull(message = "Năm sản xuất bắt buộc.")
	@Min(value = 1900, message = "Năm phải >= 1900.")
	private Integer year;

	private String description;
	private String fuel;
	private String transmission;

	@NotNull(message = "mileage bắt buộc (có thể là 0).")
	@Min(value = 0, message = "mileage phải >= 0.")
	private Integer mileage = 0;

	private String bodyStyle;
	private String origin;
	private LocalDate postingDate;

	@NotNull(message = "status bắt buộc.")
	private String status;

	@Valid
	private List<VehicleImageWriteDto> images = new ArrayList<>();

}
