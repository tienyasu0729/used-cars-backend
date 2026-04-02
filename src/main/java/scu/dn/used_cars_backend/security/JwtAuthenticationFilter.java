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

// Lọc JWT; bỏ qua filter cho login/register và GET catalog/vehicles (đọc công khai).
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final RequestMatcher PUBLIC_AUTH = new OrRequestMatcher(
			new AntPathRequestMatcher("/api/v1/auth/login", "POST"),
			new AntPathRequestMatcher("/api/v1/auth/register", "POST"));

	/** Guest: xem catalog + chi nhánh + danh sách/chi tiết xe công khai (không JWT). */
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

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final HttpErrorResponseWriter errorWriter;

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
		return PUBLIC_AUTH.matches(request) || PUBLIC_READ_CATALOG_AND_VEHICLES.matches(request);
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			errorWriter.write(response, ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.", request.getRequestURI());
			return;
		}
		String token = header.substring(7).trim();
		try {
			Claims claims = jwtService.parseClaims(token);
			Long userId = Long.parseLong(claims.getSubject());
			String roleName = claims.get("role", String.class);
			if (roleName == null || roleName.isBlank()) {
				errorWriter.write(response, ErrorCode.UNAUTHORIZED, "Token không hợp lệ.", request.getRequestURI());
				return;
			}
			Optional<User> userOpt = userRepository.findByIdAndDeletedFalse(userId);
			if (userOpt.isEmpty()) {
				errorWriter.write(response, ErrorCode.UNAUTHORIZED, "Token không hợp lệ.", request.getRequestURI());
				return;
			}
			User user = userOpt.get();
			if (!"active".equalsIgnoreCase(user.getStatus())) {
				errorWriter.write(response, ErrorCode.ACCOUNT_SUSPENDED, "Tài khoản bị khóa.", request.getRequestURI());
				return;
			}
			String authority = "ROLE_" + roleName.toUpperCase().replace(' ', '_');
			UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null,
					List.of(new SimpleGrantedAuthority(authority)));
			auth.setDetails(userId);
			SecurityContextHolder.getContext().setAuthentication(auth);
			filterChain.doFilter(request, response);
		}
		catch (JwtException | IllegalArgumentException ex) {
			errorWriter.write(response, ErrorCode.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn.",
					request.getRequestURI());
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}
}
