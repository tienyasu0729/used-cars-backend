package scu.dn.used_cars_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS: đọc pattern từ cấu hình; không dùng "*" để tránh mở hoàn toàn.
 */
@Configuration
public class AppCorsConfiguration {

	private static final List<String> DEFAULT_DEV_ORIGIN_PATTERNS = List.of(
			"http://localhost:*",
			"http://127.0.0.1:*",
			"http://[::1]:*");

	private final AppSecurityProperties appSecurityProperties;

	public AppCorsConfiguration(AppSecurityProperties appSecurityProperties) {
		this.appSecurityProperties = appSecurityProperties;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		List<String> patterns = new ArrayList<>(appSecurityProperties.getCors().getAllowedOriginPatterns());
		if (patterns.isEmpty()) {
			patterns.addAll(DEFAULT_DEV_ORIGIN_PATTERNS);
		}
		config.setAllowedOriginPatterns(patterns);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(false);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	/** Dùng chung cho WebSocket STOMP (cùng logic fallback với HTTP CORS). */
	public String[] websocketAllowedOriginPatterns() {
		List<String> patterns = new ArrayList<>(appSecurityProperties.getCors().getAllowedOriginPatterns());
		if (patterns.isEmpty()) {
			patterns.addAll(DEFAULT_DEV_ORIGIN_PATTERNS);
		}
		return patterns.toArray(new String[0]);
	}
}
