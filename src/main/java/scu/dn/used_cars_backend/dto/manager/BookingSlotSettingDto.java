package scu.dn.used_cars_backend.dto.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSlotSettingDto {

	private Integer id;
	private LocalTime slotTime;
	private Integer maxBookings;
	private Boolean isActive;
}
