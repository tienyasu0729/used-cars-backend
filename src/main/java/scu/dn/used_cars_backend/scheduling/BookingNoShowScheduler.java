package scu.dn.used_cars_backend.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import scu.dn.used_cars_backend.booking.service.BookingService;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingNoShowScheduler {

	private final BookingService bookingService;

	@Scheduled(cron = "${app.bookings.no-show-cron:0 55 23 * * *}", zone = "${app.timezone:Asia/Saigon}")
	public void autoMarkNoShowAtEndOfDay() {
		LocalDate today = LocalDate.now();
		int updated = bookingService.autoMarkOverdueBookingsAsNoShow(today);
		if (updated > 0) {
			log.info("Auto NoShow end-of-day: {} booking(s) updated for {}", updated, today);
		}
	}
}
