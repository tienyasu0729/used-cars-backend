package scu.dn.used_cars_backend.dto.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/** Lịch công khai theo thứ: 0 = CN … 6 = T7. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchPublicScheduleDto {

	private int dayOfWeek;
	private boolean closed;
	private LocalTime openTime;
	private LocalTime closeTime;
}
