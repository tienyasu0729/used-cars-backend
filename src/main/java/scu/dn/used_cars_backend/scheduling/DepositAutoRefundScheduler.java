package scu.dn.used_cars_backend.scheduling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.FinancialTransaction;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.FinancialTransactionRepository;
import scu.dn.used_cars_backend.service.InAppNotificationService;

// Scheduler doc lap — tu dong chuyen deposit RefundPending/RefundFailed sang Refunded
// sau khi don hang bi huy du lau (>= 15 phut).
// Chi xu ly deposit trong cua so 15 phut → 3 ngay ke tu khi huy don.
// Ngoai 3 ngay: bo qua (can xu ly thu cong qua nut "Danh dau da hoan").
// Scheduler nay KHONG goi gateway refund — chi cap nhat trang thai trong DB.
// An toan khi server restart: query lai tu DB, khong phu thuoc bo nho.
@Component
@RequiredArgsConstructor
public class DepositAutoRefundScheduler {

	private static final Logger log = LoggerFactory.getLogger(DepositAutoRefundScheduler.class);

	// Don hang phai huy >= 15 phut truoc moi tu dong chuyen Refunded
	private static final int GRACE_MINUTES = 15;
	// Don hang huy > 3 ngay truoc thi bo qua (can xu ly thu cong)
	private static final int MAX_DAYS = 3;

	private final DepositRepository depositRepository;
	private final FinancialTransactionRepository financialTransactionRepository;
	private final InAppNotificationService inAppNotificationService;

	// Chay moi 5 phut, bat dau 1 phut sau khi server khoi dong
	@Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
	@Transactional
	public void autoMarkRefunded() {
		// B1: Tinh khung thoi gian hop le
		Instant cutoff15min = Instant.now().minus(GRACE_MINUTES, ChronoUnit.MINUTES);
		Instant cutoff3days = Instant.now().minus(MAX_DAYS, ChronoUnit.DAYS);

		// B2: Tim deposit trong cua so hop le (don huy >= 15 phut va < 3 ngay truoc)
		List<Deposit> deposits = depositRepository
				.findRefundEligibleByOrderCancelTime(cutoff15min, cutoff3days);

		if (deposits.isEmpty()) {
			return;
		}

		log.info("[AutoRefund] Tim thay {} deposit du dieu kien tu dong chuyen Refunded.", deposits.size());

		// B3: Cap nhat tung deposit
		for (Deposit d : deposits) {
			try {
				d.setStatus("Refunded");
				String n = d.getNotes() != null ? d.getNotes() : "";
				String tag = "[auto-refund] Tu dong chuyen Refunded sau thoi gian cho (" + GRACE_MINUTES + " phut)";
				d.setNotes(n.isBlank() ? tag : n + " | " + tag);
				depositRepository.save(d);
				// Ghi row Refund vao Transactions
				insertRefundTransaction(d);
				log.info("[AutoRefund] Deposit {} da chuyen sang Refunded.", d.getId());

				// B4: Gui thong bao in-app cho khach hang
				try {
					inAppNotificationService.createNotification(
							d.getCustomerId(),
							"deposit_refunded",
							"Hoàn cọc thành công",
							"Khoản cọc #" + d.getId() + " đã được hoàn tiền thành công. "
									+ "Vui lòng kiểm tra tài khoản của bạn.",
							"/dashboard/deposits");
				} catch (Exception ne) {
					log.warn("[AutoRefund] Khong gui duoc thong bao cho khach deposit {}: {}",
							d.getId(), ne.getMessage());
				}
			} catch (Exception e) {
				log.warn("[AutoRefund] Loi khi cap nhat deposit {}: {}", d.getId(), e.getMessage());
			}
		}
	}

	private void insertRefundTransaction(Deposit d) {
		String gw = d.getPaymentGateway() != null ? d.getPaymentGateway().trim().toLowerCase() : "cash";
		if (!"vnpay".equals(gw) && !"zalopay".equals(gw)) {
			gw = "cash";
		}
		FinancialTransaction tx = new FinancialTransaction();
		tx.setUserId(d.getCustomerId());
		tx.setType("Refund");
		tx.setAmount(d.getAmount());
		tx.setStatus("Completed");
		tx.setDescription("Auto-refund coc #" + d.getId());
		tx.setReferenceId(d.getId());
		tx.setReferenceType("Deposit");
		tx.setPaymentGateway(gw);
		financialTransactionRepository.save(tx);
	}
}
