package scu.dn.used_cars_backend.service;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.User;

import java.util.regex.Pattern;

public final class ProfileCompletionSupport {

	private static final Pattern VN_PHONE = Pattern.compile("^0\\d{9}$");
	private static final Pattern PROFILE_NAME = Pattern.compile("(?U)^[\\p{L}\\p{M}0-9\\s.'’\\-]{2,100}$");

	private ProfileCompletionSupport() {
	}

	public static boolean isCustomerProfileComplete(User user) {
		if (user == null) {
			return false;
		}
		String phone = user.getPhone();
		String name = user.getName();
		if (phone == null || phone.isBlank() || name == null || name.isBlank()) {
			return false;
		}
		return VN_PHONE.matcher(phone.trim()).matches() && PROFILE_NAME.matcher(name.trim()).matches();
	}

	public static void refreshProfileCompletionFlag(User user) {
		if (user == null) {
			return;
		}
		user.setProfileCompletionRequired(!isCustomerProfileComplete(user));
	}

	public static void assertCustomerProfileComplete(User user) {
		if (user == null) {
			throw new BusinessException(ErrorCode.PROFILE_COMPLETION_REQUIRED);
		}
		if (Boolean.TRUE.equals(user.getProfileCompletionRequired()) || !isCustomerProfileComplete(user)) {
			throw new BusinessException(ErrorCode.PROFILE_COMPLETION_REQUIRED);
		}
	}
}
