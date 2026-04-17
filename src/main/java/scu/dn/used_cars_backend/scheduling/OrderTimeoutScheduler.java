package scu.dn.used_cars_backend.scheduling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import scu.dn.used_cars_backend.service.OrderService;

// Scheduler tu dong huy cac don Pending khong coc (depositAmount = 0)
// da qua han pending-timeout-hours de giai phong xe (tranh giu xe "ao").
// Don co coc van duoc giu nguyen vi coc la cam ket tai chinh.
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

	private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

	private final OrderService orderService;

	@Value("${app.order.pending-timeout-hours:24}")
	private int pendingTimeoutHours;

	// Chay moi 5 phut; initialDelay 60s de tranh chay ngay khi app start.
	@Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
	public void expirePendingDirectOrders() {
		Instant cutoff = Instant.now().minus(pendingTimeoutHours, ChronoUnit.HOURS);
		List<Long> ids = orderService.findPendingDirectOrderIdsCreatedBefore(cutoff);
		if (ids.isEmpty()) {
			return;
		}
		log.info("Auto-timeout: phat hien {} don Pending khong coc qua {} gio", ids.size(), pendingTimeoutHours);
		for (Long id : ids) {
			try {
				orderService.autoCancelTimedOutDirectOrder(id);
			}
			catch (Exception e) {
				log.warn("Khong auto-huy duoc don id={}", id, e);
			}
		}
	}
}
