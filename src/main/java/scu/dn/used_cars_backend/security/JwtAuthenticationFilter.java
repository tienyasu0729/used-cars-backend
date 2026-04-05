package scu.dn.used_cars_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final RequestMatcher PUBLIC_AUTH = new OrRequestMatcher(
			new AntPathRequestMatcher("/api/v1/auth/login", "POST"),
			new AntPathRequestMatcher("/api/v1/auth/register", "POST"));

	private static final RequestMatcher PUBLIC_READ_CATALOG_AND_VEHICLES = new OrRequestMatcher(
			new AntPathRequestMatcher("/api/v1/catalog/**", "GET"),
			new AntPathRequestMatcher("/api/v1/branches", "GET"),
			new AntPathRequestMatcher("/api/v1/branches/*", "GET"),
			new AntPathRequestMatcher("/api/v1/branches/*/team", "GET"),
			new AntPathRequestMatcher("/api/v1/vehicles", "GET"),
			new AntPathRequestMatcher("/api/v1/vehicles/*", "GET"),
			new AntPathRequestMatcher("/api/v1/vehicles/*/view", "POST"),
			new AntPathRequestMatcher("/api/v1/vehicles/recently-viewed", "GET"),
			new AntPathRequestMatcher("/api/v1/bookings/available-slots", "GET"));

	private static final RequestMatcher WS_HANDSHAKE = new AntPathRequestMatcher("/ws/**");

	private static final RequestMatcher PUBLIC_PAYMENT_GATEWAY = new OrRequestMatcher(
			new AntPathRequestMatcher("/api/v1/payment/vnpay/return", "GET"),
			new AntPathRequestMatcher("/api/v1/payment/vnpay/ipn", "GET"),
			new AntPathRequestMatcher("/api/v1/payment/vnpay/ipn", "POST"),
			new AntPathRequestMatcher("/api/v1/payment/zalopay/callback", "POST"));

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final HttpErrorResponseWriter errorWriter;

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		return PUBLIC_AUTH.matches(request) || PUBLIC_PAYMENT_GATEWAY.matches(request) || WS_HANDSHAKE.matches(request);
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (PUBLIC_READ_CATALOG_AND_VEHICLES.matches(request)) {
			if (header == null || !header.startsWith("Bearer ")) {
				filterChain.doFilter(request, response);
				return;
			}
			try {
				String token = header.substring(7).trim();
				if (authenticateWithToken(request, response, token, true)) {
					filterChain.doFilter(request, response);
				}
			}
			finally {
				SecurityContextHolder.clearContext();
			}
			return;
		}
		if (header == null || !header.startsWith("Bearer ")) {
			errorWriter.write(response, ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.", request.getRequestURI());
			return;
		}
		try {
			String token = header.substring(7).trim();
			if (authenticateWithToken(request, response, token, false)) {
				filterChain.doFilter(request, response);
			}
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private boolean authenticateWithToken(HttpServletRequest request, HttpServletResponse response, String token,
			boolean optionalPublicRead) throws IOException {
		try {
			Claims claims = jwtService.parseClaims(token);
			Long userId = Long.parseLong(claims.getSubject());
			String roleName = claims.get("role", String.class);
			if (roleName == null || roleName.isBlank()) {
				errorWriter.write(response, ErrorCode.UNAUTHORIZED, "Token không hợp lệ.", request.getRequestURI());
				return false;
			}
			Optional<User> userOpt = userRepository.findByIdAndDeletedFalse(userId);
			if (userOpt.isEmpty()) {
				errorWriter.write(response, ErrorCode.UNAUTHORIZED, "Token không hợp lệ.", request.getRequestURI());
				return false;
			}
			User user = userOpt.get();
			if (!"active".equalsIgnoreCase(user.getStatus())) {
				errorWriter.write(response, ErrorCode.ACCOUNT_SUSPENDED, "Tài khoản bị khóa.", request.getRequestURI());
				return false;
			}
			if (!optionalPublicRead && Boolean.TRUE.equals(user.getPasswordChangeRequired())
					&& !isPasswordChangeMandatoryExempt(request)) {
				errorWriter.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED,
						"Vui lòng đặt mật khẩu mới trước khi tiếp tục.", request.getRequestURI());
				return false;
			}
			String authority = "ROLE_" + roleName.toUpperCase().replace(' ', '_');
			UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null,
					List.of(new SimpleGrantedAuthority(authority)));
			auth.setDetails(userId);
			SecurityContextHolder.getContext().setAuthentication(auth);
			return true;
		}
		catch (JwtException | IllegalArgumentException ex) {
			errorWriter.write(response, ErrorCode.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn.",
					request.getRequestURI());
			return false;
		}
	}

	private static boolean isPasswordChangeMandatoryExempt(HttpServletRequest request) {
		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			return false;
		}
		String uri = request.getRequestURI();
		return uri.contains("/auth/complete-required-password-change") || uri.contains("/auth/logout");
	}
}
