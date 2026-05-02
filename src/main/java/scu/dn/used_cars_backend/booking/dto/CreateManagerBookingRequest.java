package scu.dn.used_cars_backend.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import scu.dn.used_cars_backend.dto.sales.ShowroomCustomerInfo;

@Data
public class CreateManagerBookingRequest {

	@NotNull
	private Long vehicleId;

	@NotNull
	private Integer branchId;

	@NotBlank
	private String bookingDate;

	@NotBlank
	private String timeSlot;

	@NotBlank
	private String type;

	private String note;

	@Valid
	@NotNull
	private ShowroomCustomerInfo customer;
}
