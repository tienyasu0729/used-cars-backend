package scu.dn.used_cars_backend.scheduling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.service.DepositService;
import scu.dn.used_cars_backend.service.payment.PaymentApplicationService;
import scu.dn.used_cars_backend.service.payment.PaymentGatewayConfigService;
import scu.dn.used_cars_backend.service.payment.ZaloPayService;

@Component
@RequiredArgsConstructor
public class DepositPendingPaymentExpiryScheduler {

	private static final Logger log = LoggerFactory.getLogger(DepositPendingPaymentExpiryScheduler.class);

	private final DepositService depositService;
	private final DepositRepository depositRepository;
	private final PaymentApplicationService paymentApplicationService;
	private final PaymentGatewayConfigService paymentGatewayConfigService;
	private final ZaloPayService zaloPayService;

	@Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
	public void expireStalePendingOnlineDeposits() {
		String raw = paymentGatewayConfigService
				.getOptional(PaymentGatewayConfigService.KEY_DEPOSIT_ONLINE_PAYMENT_TIMEOUT_MINUTES);
		int minutes = DepositService.parseOnlinePaymentTimeoutMinutes(raw);
		Instant cutoff = Instant.now().minus(minutes, ChronoUnit.MINUTES);
		List<Long> ids = depositService.findPendingOnlineDepositIdsExpiredBefore(cutoff);
		for (Long id : ids) {
			try {
				Deposit d = depositRepository.findById(id).orElse(null);
				if (d != null && d.getGatewayTxnRef() != null
						&& "zalopay".equalsIgnoreCase(d.getPaymentGateway())) {
					var cfg = paymentGatewayConfigService.loadZaloPayForCreate();
					JsonNode gw = zaloPayService.queryOrderStatus(cfg, d.getGatewayTxnRef());
					boolean synced = paymentApplicationService.maybeSyncDepositFromZaloQuery(d.getId(), gw);
					if (synced) {
						log.info("Deposit {} synced from ZaloPay query before timeout cancel", id);
						continue;
					}
				}
				depositService.cancelPendingOnlineDepositTimedOut(id);
			}
			catch (Exception e) {
				log.warn("Khong huy duoc coc Pending het han id={}", id, e);
			}
		}
		List<Long> payIds = paymentApplicationService.findPendingOnlineOrderPaymentIdsExpiredBefore(cutoff);
		for (Long pid : payIds) {
			try {
				paymentApplicationService.cancelPendingOrderPayment(pid);
			}
			catch (Exception e) {
				log.warn("Khong dong duoc OrderPayment Pending het han id={}", pid, e);
			}
		}
	}
}
