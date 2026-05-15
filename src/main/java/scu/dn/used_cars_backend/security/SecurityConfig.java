package scu.dn.used_cars_backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import scu.dn.used_cars_backend.sms.filter.DeviceKeyFilter;
import scu.dn.used_cars_backend.sms.filter.SmsHttpsFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
	private final CorsConfigurationSource corsConfigurationSource;
	private final DeviceKeyFilter deviceKeyFilter;
	private final SmsHttpsFilter smsHttpsFilter;

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.cors(c -> c.configurationSource(corsConfigurationSource))
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(e -> e.authenticationEntryPoint(restAuthenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/api/sms/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/consultations").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register",
								"/api/v1/auth/register/request-otp", "/api/v1/auth/google",
								"/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/branches", "/api/v1/branches/*", "/api/v1/branches/*/team")
								.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/vehicles", "/api/v1/vehicles/*",
								"/api/v1/vehicles/*/maintenance").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/vehicles/*/view").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/articles", "/api/v1/articles/categories",
								"/api/v1/articles/*").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/vehicles/*/reviews",
								"/api/v1/vehicles/*/reviews/summary").permitAll()
						.requestMatchers("/api/v1/public/document-sessions/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/bookings/available-slots").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/payment/vnpay/return").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/payment/zalopay/return").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/payment/vnpay/ipn").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/payment/vnpay/ipn").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/payment/zalopay/callback").permitAll()
						.requestMatchers("/api/v1/webhook/**").permitAll()
						.requestMatchers("/error").permitAll()
						.requestMatchers("/ws/**").permitAll()
						.requestMatchers("/api/v1/admin/transactions/**").hasAnyRole("ADMIN", "BRANCHMANAGER")
						// Các admin path mở rộng cho permission-based access (kiểm tra quyền qua @PreAuthorize)
						.requestMatchers("/api/v1/admin/home-banners/**").authenticated()
						.requestMatchers("/api/v1/admin/branches/**").authenticated()
						.requestMatchers("/api/v1/admin/catalog/**").authenticated()
						.requestMatchers("/api/v1/admin/notifications/**").authenticated()
						.requestMatchers("/api/v1/admin/articles/**").authenticated()
						.requestMatchers("/api/v1/admin/reviews/**").authenticated()
						// Các admin path còn lại — chỉ Admin
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/manager/staff/**").hasAnyRole("ADMIN", "BRANCHMANAGER")
						.requestMatchers("/api/v1/manager/settings/**").hasAnyRole("ADMIN", "BRANCHMANAGER")
						.requestMatchers("/api/v1/manager/dashboard/**").hasAnyRole("ADMIN", "BRANCHMANAGER")
						.requestMatchers("/api/v1/manager/reports/**").authenticated()
						.requestMatchers("/api/v1/manager/media/**").hasAnyRole("ADMIN", "BRANCHMANAGER")
						.requestMatchers("/api/v1/staff/dashboard/**").hasAnyRole("ADMIN", "BRANCHMANAGER", "SALESSTAFF")
						.anyRequest().authenticated())
				.addFilterBefore(smsHttpsFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(deviceKeyFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
