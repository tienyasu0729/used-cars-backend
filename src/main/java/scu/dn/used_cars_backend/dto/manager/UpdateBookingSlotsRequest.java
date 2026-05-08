package scu.dn.used_cars_backend.dto.manager;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
public class UpdateBookingSlotsRequest {

	@NotEmpty(message = "Danh sách khung giờ không được rỗng.")
	@Valid
	private List<SlotItem> slots;

	@Data
	public static class SlotItem {

		@NotNull(message = "slotTime không được null.")
		private LocalTime slotTime;

		@NotNull(message = "maxBookings không được null.")
		private Integer maxBookings;

		@NotNull(message = "isActive không được null.")
		private Boolean isActive;
	}
}
