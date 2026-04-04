package scu.dn.used_cars_backend.scheduling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import scu.dn.used_cars_backend.service.DepositService;
import scu.dn.used_cars_backend.service.payment.PaymentGatewayConfigService;

@Component
@RequiredArgsConstructor
public class DepositPendingPaymentExpiryScheduler {

	private static final Logger log = LoggerFactory.getLogger(DepositPendingPaymentExpiryScheduler.class);

	private final DepositService depositService;
	private final PaymentGatewayConfigService paymentGatewayConfigService;

	@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
	public void expireStalePendingOnlineDeposits() {
		String raw = paymentGatewayConfigService
				.getOptional(PaymentGatewayConfigService.KEY_DEPOSIT_ONLINE_PAYMENT_TIMEOUT_MINUTES);
		int minutes = DepositService.parseOnlinePaymentTimeoutMinutes(raw);
		Instant cutoff = Instant.now().minus(minutes, ChronoUnit.MINUTES);
		List<Long> ids = depositService.findPendingOnlineDepositIdsExpiredBefore(cutoff);
		for (Long id : ids) {
			try {
				depositService.cancelPendingOnlineDepositTimedOut(id);
			}
			catch (Exception e) {
				log.warn("Khong huy duoc coc Pending het han id={}", id, e);
			}
		}
	}
}
