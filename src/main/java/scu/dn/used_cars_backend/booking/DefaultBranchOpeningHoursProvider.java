package scu.dn.used_cars_backend.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import scu.dn.used_cars_backend.entity.BranchWorkingHours;
import scu.dn.used_cars_backend.repository.BranchWorkingHoursRepository;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Lọc khung giờ đặt lịch theo {@link BranchWorkingHours} thực tế của chi nhánh.
 * Ngày đóng cửa ({@code is_closed = true}) hoặc không có bản ghi → không coi giờ nào hợp lệ.
 */
@Component
@RequiredArgsConstructor
public class DefaultBranchOpeningHoursProvider implements BranchOpeningHoursProvider {

	private final BranchWorkingHoursRepository branchWorkingHoursRepository;

	/**
	 * Map {@link LocalDate} sang {@code day_of_week} DDL (0 = CN, 1 = T2, …, 6 = T7).
	 */
	static int dayOfWeekForSchema(LocalDate date) {
		return date.getDayOfWeek().getValue() % 7;
	}

	@Override
	public boolean isWithinWorkingHours(int branchId, LocalDate date, LocalTime time) {
		int dow = dayOfWeekForSchema(date);
		return branchWorkingHoursRepository.findByBranch_IdAndDayOfWeek(branchId, dow)
				.filter(h -> !h.isClosed())
				.map(h -> isTimeWithinOpenClose(h, time))
				.orElse(false);
	}

	private static boolean isTimeWithinOpenClose(BranchWorkingHours h, LocalTime time) {
		LocalTime open = h.getOpenTime();
		LocalTime close = h.getCloseTime();
		if (open == null || close == null) {
			return false;
		}
		if (open.equals(close)) {
			return false;
		}
		// Cùng ngày: [open, close)
		if (close.isAfter(open)) {
			return !time.isBefore(open) && time.isBefore(close);
		}
		// Ca đêm: [open, 24:00) ∪ [00:00, close)
		return !time.isBefore(open) || time.isBefore(close);
	}
}
