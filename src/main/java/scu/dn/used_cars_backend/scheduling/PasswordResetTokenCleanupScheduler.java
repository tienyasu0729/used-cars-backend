package scu.dn.used_cars_backend.scheduling;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import scu.dn.used_cars_backend.repository.PasswordResetTokenRepository;

import java.time.Instant;

// Dọn dẹp token đặt lại mật khẩu đã hết hạn, chạy mỗi giờ.
@Component
@RequiredArgsConstructor
public class PasswordResetTokenCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenCleanupScheduler.class);

	private final PasswordResetTokenRepository passwordResetTokenRepository;

	@Scheduled(fixedDelay = 3600_000, initialDelay = 60_000)
	@Transactional
	public void cleanupExpiredTokens() {
		try {
			passwordResetTokenRepository.deleteByExpiresAtBefore(Instant.now());
		} catch (Exception e) {
			log.warn("Lỗi khi dọn dẹp token đặt lại mật khẩu hết hạn: {}", e.getMessage());
		}
	}
}
