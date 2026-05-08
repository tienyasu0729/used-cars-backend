package scu.dn.used_cars_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.credit-service")
public record FakeBankProperties(
		String url,
		String statusEndpoint,
		String apiKey,
		String secret,
		Integer timeoutMs,
		Integer connectTimeoutMs,
		Retry retry) {

	public record Retry(
			Long baseDelayMs,
			Long maxDelayMs,
			Integer batchSize) {
	}
}
