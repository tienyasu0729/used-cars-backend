package scu.dn.used_cars_backend.dto.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/** Một ngày trong tuần: 0 = CN … 6 = T7. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDayScheduleDto {

	private int dayOfWeek;
	private boolean closed;
	private LocalTime openTime;
	private LocalTime closeTime;
}
