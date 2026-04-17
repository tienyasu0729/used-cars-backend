package scu.dn.used_cars_backend.scheduling;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.service.payment.PaymentApplicationService;

// Scheduler tu dong retry hoan tien coc cho cac deposit dang o trang thai RefundPending hoac RefundFailed.
// Xu ly 2 truong hop:
//   1. Du lieu cu bi ket RefundPending truoc khi deploy code refund moi
//   2. Hoan tien that bai do mat mang / gateway loi tam thoi
// Chay moi 5 phut, gioi han retry 10 lan (dem qua notes).
@Component
@RequiredArgsConstructor
public class DepositRefundRetryScheduler {

	private static final Logger log = LoggerFactory.getLogger(DepositRefundRetryScheduler.class);
	private static final int MAX_RETRY = 10;
	private static final String RETRY_PREFIX = "[refund-retry:";
	private static final String NO_RETRY_TAG = "[refund-no-retry]";

	private final DepositRepository depositRepository;
	private final PaymentApplicationService paymentApplicationService;

	// Chay moi 5 phut, bat dau sau 30 giay khi server khoi dong
	@Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
	public void retryPendingRefunds() {
		List<Deposit> deposits = depositRepository.findByStatusIn(
				List.of("RefundPending", "RefundFailed"));

		if (deposits.isEmpty()) {
			return;
		}

		log.info("Tim thay {} deposit can retry hoan tien.", deposits.size());

		for (Deposit d : deposits) {
			try {
				processOne(d);
			} catch (Exception e) {
				log.warn("Loi khi retry refund deposit {}: {}", d.getId(), e.getMessage());
			}
		}
	}

	@Transactional
	protected void processOne(Deposit d) {
		// B1: Kiem tra deposit da bi danh dau loi vinh vien (VNPay: du lieu sai, giao dich khong ton tai...)
		String notes = d.getNotes();
		if (notes != null && notes.contains(NO_RETRY_TAG)) {
			log.info("Deposit {} bi loi vinh vien (gateway tu choi), bo qua retry.", d.getId());
			return;
		}

		// B2: Dem so lan retry tu notes
		int retryCount = parseRetryCount(notes);
		if (retryCount >= MAX_RETRY) {
			return;
		}

		// B2: Goi refund
		boolean success = paymentApplicationService.refundDeposit(d);

		// B3: Cap nhat trang thai
		if (success) {
			d.setStatus("Refunded");
			log.info("Retry refund deposit {} thanh cong (lan thu {}).", d.getId(), retryCount + 1);
		} else {
			d.setStatus("RefundFailed");
			d.setNotes(appendRetry(d.getNotes(), retryCount + 1));
			log.warn("Retry refund deposit {} that bai lan {} / {}.", d.getId(), retryCount + 1, MAX_RETRY);
		}
		depositRepository.save(d);
	}

	// Doc so lan retry tu notes, vi du: "[refund-retry:3]"
	private static int parseRetryCount(String notes) {
		if (notes == null || !notes.contains(RETRY_PREFIX)) {
			return 0;
		}
		int start = notes.lastIndexOf(RETRY_PREFIX) + RETRY_PREFIX.length();
		int end = notes.indexOf(']', start);
		if (end < 0) {
			return 0;
		}
		try {
			return Integer.parseInt(notes.substring(start, end).trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// Ghi so lan retry vao notes, vi du: "[refund-retry:4]"
	private static String appendRetry(String notes, int count) {
		String tag = RETRY_PREFIX + count + "]";
		if (notes == null || notes.isBlank()) {
			return tag;
		}
		// Xoa tag cu neu co
		String cleaned = notes.replaceAll("\\[refund-retry:\\d+]", "").trim();
		if (cleaned.isEmpty()) {
			return tag;
		}
		return cleaned + " | " + tag;
	}
}
