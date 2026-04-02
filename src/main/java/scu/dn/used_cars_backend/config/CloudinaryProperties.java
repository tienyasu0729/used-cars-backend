package scu.dn.used_cars_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret) {

	public boolean uploadConfigured() {
		return nz(cloudName) && nz(apiKey) && nz(apiSecret);
	}

	private static boolean nz(String s) {
		return s != null && !s.isBlank();
	}
}
