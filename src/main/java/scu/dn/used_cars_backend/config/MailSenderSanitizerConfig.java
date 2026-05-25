package scu.dn.used_cars_backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Chuẩn hóa username/password trên JavaMailSender (bỏ khoảng trắng App Password Gmail)
 * và log chẩn đoán một lần khi khởi động — không đọc/sửa application.yml.
 */
@Configuration
public class MailSenderSanitizerConfig {

	private static final Logger log = LoggerFactory.getLogger(MailSenderSanitizerConfig.class);

	private final Environment environment;

	public MailSenderSanitizerConfig(Environment environment) {
		this.environment = environment;
	}

	@Bean
	public BeanPostProcessor javaMailSenderSanitizer() {
		return new BeanPostProcessor() {
			private boolean logged;

			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
				if (!(bean instanceof JavaMailSenderImpl impl)) {
					return bean;
				}
				sanitize(impl);
				if (!logged) {
					logged = true;
					logStartup(impl);
				}
				return bean;
			}
		};
	}

	private void sanitize(JavaMailSenderImpl impl) {
		String user = impl.getUsername();
		if (user != null) {
			impl.setUsername(user.trim());
		}
		String pwd = impl.getPassword();
		if (pwd != null) {
			String normalized = pwd.replaceAll("\\s+", "").trim();
			if (!normalized.equals(pwd)) {
				impl.setPassword(normalized);
				log.info("SMTP: đã bỏ khoảng trắng trong App Password (Gmail dùng 16 ký tự liền, không có dấu cách).");
			}
		}
	}

	private void logStartup(JavaMailSenderImpl impl) {
		String host = impl.getHost();
		if (host == null || host.isBlank()) {
			return;
		}
		String pwd = impl.getPassword();
		int pwdLen = (pwd != null) ? pwd.length() : 0;
		log.info(
				"SMTP sẵn sàng: host={} port={} user={} password.length={} nguồn={}",
				host,
				impl.getPort(),
				SmtpDiagnostics.maskEmail(impl.getUsername()),
				pwdLen,
				SmtpDiagnostics.passwordSourceHint(environment));
		if (host.contains("gmail") && pwdLen != 16 && pwdLen > 0) {
			log.warn(
					"Gmail App Password thường có đúng 16 ký tự (hiện tại {}). "
							+ "Tạo mới tại https://myaccount.google.com/apppasswords cho đúng tài khoản {}",
					pwdLen,
					SmtpDiagnostics.maskEmail(impl.getUsername()));
		}
	}
}
