package scu.dn.used_cars_backend.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateBookingRequest {

	@NotNull
	private Long vehicleId;

	@NotNull
	private Integer branchId;

	@NotBlank
	private String bookingDate;

	@NotBlank
	private String timeSlot;

	private String note;

	private String phone;

	private String otpCode;
}
