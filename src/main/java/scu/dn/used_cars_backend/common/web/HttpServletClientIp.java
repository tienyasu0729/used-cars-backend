package scu.dn.used_cars_backend.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpServletClientIp {

	private HttpServletClientIp() {
	}

	public static String resolve(HttpServletRequest request) {
		if (request == null) {
			return "127.0.0.1";
		}
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			int c = xff.indexOf(',');
			String first = (c > 0 ? xff.substring(0, c) : xff).trim();
			if (!first.isBlank()) {
				return first;
			}
		}
		String xri = request.getHeader("X-Real-IP");
		if (xri != null && !xri.isBlank()) {
			return xri.trim();
		}
		String tc = request.getHeader("True-Client-IP");
		if (tc != null && !tc.isBlank()) {
			return tc.trim();
		}
		String cf = request.getHeader("CF-Connecting-IP");
		if (cf != null && !cf.isBlank()) {
			return cf.trim();
		}
		String ip = request.getRemoteAddr();
		return ip != null && !ip.isBlank() ? ip : "127.0.0.1";
	}
}
