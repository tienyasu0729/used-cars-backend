package scu.dn.used_cars_backend.security;

import org.springframework.security.core.Authentication;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;

// Lấy userId (Long) đặt bởi filter JWT trong SecurityContext — dùng chung controller profile/auth.
public final class AuthenticationDetailsUtils {

	private AuthenticationDetailsUtils() {
	}

	public static long requireUserId(Authentication authentication) {
		if (authentication == null || authentication.getDetails() == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		return (Long) authentication.getDetails();
	}

	/** Lấy userId nếu đã login, null nếu chưa — dùng cho API công khai muốn enrich response cho user. */
	public static Long optionalUserId(Authentication authentication) {
		if (authentication == null || authentication.getDetails() == null) {
			return null;
		}
		try {
			return (Long) authentication.getDetails();
		} catch (ClassCastException e) {
			return null;
		}
	}
}
