package scu.dn.used_cars_backend.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import scu.dn.used_cars_backend.booking.service.BookingService;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingNoShowScheduler {

	private final BookingService bookingService;

	@Scheduled(cron = "${app.bookings.no-show-cron:0 */5 * * * *}", zone = "${app.timezone:Asia/Saigon}")
	public void autoMarkNoShowAtEndOfDay() {
		LocalDateTime now = LocalDateTime.now();
		int updated = bookingService.autoMarkOverdueBookingsAsNoShow(now);
		if (updated > 0) {
			log.info("Auto cancel no-show: {} booking(s) updated at {}", updated, now);
		}
	}
}
