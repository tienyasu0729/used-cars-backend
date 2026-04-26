package scu.dn.used_cars_backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.common.web.HttpServletClientIp;
import scu.dn.used_cars_backend.config.AppSecurityProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn số request POST /api/v1/auth/login và /register theo IP (Bucket4j).
 * Lỗi bất kỳ trong filter → bỏ qua giới hạn (fail-open) để không chặn traffic.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

	private final AppSecurityProperties appSecurityProperties;
	private final HttpErrorResponseWriter httpErrorResponseWriter;

	private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!appSecurityProperties.getRateLimit().isEnabled()) {
			return true;
		}
		if (!HttpMethod.POST.matches(request.getMethod())) {
			return true;
		}
		String uri = request.getRequestURI();
		return !"/api/v1/auth/login".equals(uri) && !"/api/v1/auth/register".equals(uri);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try {
			int perMinute = Math.max(1, appSecurityProperties.getRateLimit().getRequestsPerMinute());
			String ip = HttpServletClientIp.resolve(request);
			Bucket bucket = cache.computeIfAbsent(ip, k -> newBucket(perMinute));
			if (bucket.tryConsume(1)) {
				filterChain.doFilter(request, response);
			}
			else {
				httpErrorResponseWriter.write(response, ErrorCode.RATE_LIMITED,
						"Quá nhiều yêu cầu. Vui lòng thử lại sau.", request.getRequestURI());
			}
		}
		catch (Exception e) {
			log.warn("Rate limit lỗi (fail-open, cho phép request): {}", e.toString());
			filterChain.doFilter(request, response);
		}
	}

	private static Bucket newBucket(int requestsPerMinute) {
		Refill refill = Refill.greedy(requestsPerMinute, Duration.ofMinutes(1));
		Bandwidth limit = Bandwidth.classic(requestsPerMinute, refill);
		return Bucket.builder().addLimit(limit).build();
	}
}
