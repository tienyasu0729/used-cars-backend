package scu.dn.used_cars_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.credit-service")
public record FakeBankProperties(String url, String apiKey, String secret) {
}
