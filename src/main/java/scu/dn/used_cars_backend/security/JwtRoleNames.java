package scu.dn.used_cars_backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Locale;

public final class JwtRoleNames {

	private static final Logger log = LoggerFactory.getLogger(JwtRoleNames.class);

	private JwtRoleNames() {
	}

	/**
	 * Chuẩn hóa tên role JWT về UPPERCASE + gạch dưới (khớp {@link JwtAuthenticationFilter}).
	 */
	public static String normalizeRoleName(String raw) {
		if (raw == null) {
			return "";
		}
		String t = raw.trim();
		if (t.isEmpty()) {
			return "";
		}
		return t.toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	public static String primaryRole(Authentication authentication) {
		if (authentication == null) {
			return "";
		}
		for (GrantedAuthority ga : authentication.getAuthorities()) {
			String a = ga.getAuthority();
			if (a != null && a.startsWith("ROLE_")) {
				String inner = a.substring(5);
				String normalized = normalizeRoleName(inner);
				if (!normalized.equals(inner) && log.isDebugEnabled()) {
					log.debug("Chuẩn hóa role từ authority: [{}] -> [{}]", inner, normalized);
				}
				return normalized;
			}
		}
		return "";
	}

	public static boolean isAdmin(Authentication authentication) {
		if (authentication == null) {
			return false;
		}
		for (GrantedAuthority ga : authentication.getAuthorities()) {
			if ("ROLE_ADMIN".equals(ga.getAuthority())) {
				return true;
			}
		}
		return false;
	}
}
