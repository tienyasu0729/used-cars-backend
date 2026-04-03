package scu.dn.used_cars_backend.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Profile("!test")
@Order(100)
@RequiredArgsConstructor
public class UnifiedApiAuditAspect {

	private static final String API_PREFIX = "/api/v1/";

	private final AuditLogWriter auditLogWriter;

	@Around("within(@org.springframework.web.bind.annotation.RestController *) && within(scu.dn.used_cars_backend..*)")
	public Object aroundApi(ProceedingJoinPoint pjp) throws Throwable {
		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		if (attrs == null) {
			return pjp.proceed();
		}
		HttpServletRequest req = attrs.getRequest();
		String method = req.getMethod();
		if ("OPTIONS".equalsIgnoreCase(method)) {
			return pjp.proceed();
		}
		String path = normalizePath(pathOnly(req.getRequestURI()), req.getContextPath());
		if (!path.startsWith(API_PREFIX)) {
			return pjp.proceed();
		}
		Throwable failure = null;
		try {
			return pjp.proceed();
		}
		catch (Throwable t) {
			failure = t;
			throw t;
		}
		finally {
			try {
				String uri = req.getRequestURI();
				String action = method + " " + uri;
				String module = moduleFromApiPath(path);
				String ip = clientIp(req);
				Long userId = null;
				String userName = null;
				Authentication auth = SecurityContextHolder.getContext().getAuthentication();
				if (auth != null) {
					userName = auth.getName();
					if (auth.getDetails() instanceof Long uid) {
						userId = uid;
					}
				}
				String details = failureDetails(failure);
				auditLogWriter.persist(userId, userName, module, action, details, ip);
			}
			catch (RuntimeException ignored) {
			}
		}
	}

	private static String normalizePath(String path, String contextPath) {
		if (contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) {
			return path;
		}
		if (path.startsWith(contextPath)) {
			String p = path.substring(contextPath.length());
			return p.isEmpty() || p.charAt(0) == '/' ? (p.isEmpty() ? "/" : p) : "/" + p;
		}
		return path;
	}

	static String moduleFromApiPath(String path) {
		if (!path.startsWith(API_PREFIX)) {
			return "api";
		}
		String rest = path.substring(API_PREFIX.length());
		int slash = rest.indexOf('/');
		String first = slash < 0 ? rest : rest.substring(0, slash);
		if (first.isEmpty()) {
			return "api";
		}
		if ("admin".equals(first)) {
			String after = slash < 0 ? "" : rest.substring(slash + 1);
			int s2 = after.indexOf('/');
			String second = s2 < 0 ? after : after.substring(0, s2);
			if (!second.isEmpty()) {
				return truncSeg(second);
			}
		}
		return truncSeg(first);
	}

	private static String truncSeg(String s) {
		if (s.length() <= 50) {
			return s;
		}
		return s.substring(0, 50);
	}

	private static String failureDetails(Throwable err) {
		if (err == null) {
			return null;
		}
		String m = err.getClass().getSimpleName();
		String msg = err.getMessage();
		if (msg != null && !msg.isBlank()) {
			m = m + ": " + msg.trim();
		}
		return m.length() > 500 ? m.substring(0, 500) : m;
	}

	private static String pathOnly(String uri) {
		int q = uri.indexOf('?');
		return q >= 0 ? uri.substring(0, q) : uri;
	}

	private static String clientIp(HttpServletRequest req) {
		String xff = req.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			return xff.split(",")[0].trim();
		}
		return req.getRemoteAddr();
	}
}
