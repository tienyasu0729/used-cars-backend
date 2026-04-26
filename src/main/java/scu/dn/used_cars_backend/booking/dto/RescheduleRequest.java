package scu.dn.used_cars_backend.booking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RescheduleRequest {

	@NotBlank
	private String newBookingDate;

	@NotBlank
	private String newTimeSlot;

	private String note;
}
