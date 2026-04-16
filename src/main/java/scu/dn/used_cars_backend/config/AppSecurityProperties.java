package scu.dn.used_cars_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình bảo mật bổ sung: CORS, rate limit auth, whitelist IP webhook thanh toán.
 * Bind từ app.security.* trong application.yml / application-local.yml.
 */
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

	private Cors cors = new Cors();
	private RateLimit rateLimit = new RateLimit();
	private Webhook webhook = new Webhook();

	public Cors getCors() {
		return cors;
	}

	public void setCors(Cors cors) {
		this.cors = cors;
	}

	public RateLimit getRateLimit() {
		return rateLimit;
	}

	public void setRateLimit(RateLimit rateLimit) {
		this.rateLimit = rateLimit;
	}

	public Webhook getWebhook() {
		return webhook;
	}

	public void setWebhook(Webhook webhook) {
		this.webhook = webhook;
	}

	public static class Cors {
		/** Danh sách pattern origin (Spring CORS). Rỗng → dùng mặc định localhost trong code. */
		private List<String> allowedOriginPatterns = new ArrayList<>();

		public List<String> getAllowedOriginPatterns() {
			return allowedOriginPatterns;
		}

		public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
			this.allowedOriginPatterns = allowedOriginPatterns != null ? allowedOriginPatterns : new ArrayList<>();
		}
	}

	public static class RateLimit {
		private boolean enabled = true;
		/** Số request tối đa mỗi IP mỗi phút cho POST /auth/login và /auth/register */
		private int requestsPerMinute = 60;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getRequestsPerMinute() {
			return requestsPerMinute;
		}

		public void setRequestsPerMinute(int requestsPerMinute) {
			this.requestsPerMinute = requestsPerMinute;
		}
	}

	public static class Webhook {
		/** CIDR hoặc IP đơn; rỗng = không chặn (tương thích triển khai cũ). */
		private List<String> vnpayAllowCidrs = new ArrayList<>();
		private List<String> zalopayAllowCidrs = new ArrayList<>();

		public List<String> getVnpayAllowCidrs() {
			return vnpayAllowCidrs;
		}

		public void setVnpayAllowCidrs(List<String> vnpayAllowCidrs) {
			this.vnpayAllowCidrs = vnpayAllowCidrs != null ? vnpayAllowCidrs : new ArrayList<>();
		}

		public List<String> getZalopayAllowCidrs() {
			return zalopayAllowCidrs;
		}

		public void setZalopayAllowCidrs(List<String> zalopayAllowCidrs) {
			this.zalopayAllowCidrs = zalopayAllowCidrs != null ? zalopayAllowCidrs : new ArrayList<>();
		}
	}
}
