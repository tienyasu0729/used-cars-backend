package scu.dn.used_cars_backend.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGroupResponse {

	@JsonFormat(pattern = "HH:mm")
	private LocalTime timeSlot;

	private List<BookingResponse> bookings;
}
