package scu.dn.used_cars_backend.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import scu.dn.used_cars_backend.service.InstallmentService;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstallmentPreDepositExpiryScheduler {

	private final InstallmentService installmentService;

	@Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
	public void cleanupExpiredInstallmentPreDeposits() {
		int cleaned = installmentService.cleanupExpiredPreDepositPendingApplications();
		if (cleaned > 0) {
			log.info("Installment pre-deposit expiry cleanup: {} application(s) auto-cancelled", cleaned);
		}
	}
}

