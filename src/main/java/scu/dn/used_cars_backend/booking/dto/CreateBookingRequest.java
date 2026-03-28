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

	/** ISO-8601 date: yyyy-MM-dd */
	@NotBlank
	private String bookingDate;

	/** HH:mm */
	@NotBlank
	private String timeSlot;

	private String note;
}
