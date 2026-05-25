package scu.dn.used_cars_backend.config;

import org.springframework.core.env.Environment;

/**
 * Gợi ý chẩn đoán SMTP — không log mật khẩu thật.
 */
public final class SmtpDiagnostics {

	private SmtpDiagnostics() {
	}

	public static String maskEmail(String email) {
		if (email == null || email.isBlank()) {
			return "(trống)";
		}
		String t = email.trim();
		int at = t.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		if (at == 1) {
			return "*" + t.substring(at);
		}
		return t.charAt(0) + "***" + t.substring(at);
	}

	/** Nguồn đang ghi đè spring.mail.password (ưu tiên env Spring Boot). */
	public static String passwordSourceHint(Environment env) {
		if (env.containsProperty("SPRING_MAIL_PASSWORD")) {
			return "SPRING_MAIL_PASSWORD (biến môi trường)";
		}
		if (env.containsProperty("MAIL_PASSWORD")) {
			return "MAIL_PASSWORD (biến môi trường)";
		}
		return "file cấu hình (spring.mail.password)";
	}
}
