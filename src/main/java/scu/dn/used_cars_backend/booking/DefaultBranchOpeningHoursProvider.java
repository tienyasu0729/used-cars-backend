package scu.dn.used_cars_backend.booking;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class DefaultBranchOpeningHoursProvider implements BranchOpeningHoursProvider {

	@Override
	public boolean isWithinWorkingHours(int branchId, LocalDate date, LocalTime time) {
		return true;
	}
}
