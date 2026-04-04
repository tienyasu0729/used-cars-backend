package scu.dn.used_cars_backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class JwtRoleNames {

	private JwtRoleNames() {
	}

	public static String primaryRole(Authentication authentication) {
		if (authentication == null) {
			return "";
		}
		for (GrantedAuthority ga : authentication.getAuthorities()) {
			String a = ga.getAuthority();
			if (a != null && a.startsWith("ROLE_")) {
				return a.substring(5);
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
