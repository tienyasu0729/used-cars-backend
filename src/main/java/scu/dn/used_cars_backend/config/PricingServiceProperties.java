package scu.dn.used_cars_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pricing")
public class PricingServiceProperties {

	private boolean enabled;
	private String baseUrl = "";
	private String estimatePath = "/internal/vehicle-pricing/estimate";
	private String internalToken = "";
	private int connectTimeoutMs = 15000;
	private int readTimeoutMs = 600000;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl == null ? "" : baseUrl;
	}

	public String getEstimatePath() {
		return estimatePath;
	}

	public void setEstimatePath(String estimatePath) {
		this.estimatePath = estimatePath == null || estimatePath.isBlank()
				? "/internal/vehicle-pricing/estimate"
				: estimatePath;
	}

	public String getInternalToken() {
		return internalToken;
	}

	public void setInternalToken(String internalToken) {
		this.internalToken = internalToken == null ? "" : internalToken;
	}

	public int getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public void setConnectTimeoutMs(int connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs > 0 ? connectTimeoutMs : 15000;
	}

	public int getReadTimeoutMs() {
		return readTimeoutMs;
	}

	public void setReadTimeoutMs(int readTimeoutMs) {
		this.readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : 600000;
	}

	public boolean ready() {
		return enabled && !baseUrl.isBlank() && !internalToken.isBlank();
	}
}
