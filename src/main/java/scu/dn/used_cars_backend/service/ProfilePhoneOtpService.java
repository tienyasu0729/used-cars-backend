package scu.dn.used_cars_backend.service;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.sms.dto.OtpResponse;
import scu.dn.used_cars_backend.sms.dto.OtpVerifyResult;
import scu.dn.used_cars_backend.sms.service.OtpService;

@Service
@RequiredArgsConstructor
public class ProfilePhoneOtpService {

	private static final String REFERENCE_TYPE = "profile";

	private final OtpService otpService;
	private final UserRepository userRepository;

	public OtpResponse requestOtp(long userId, String phone) {
		assertPhoneAvailableForUser(userId, phone);
		return otpService.findPendingOtp(phone, REFERENCE_TYPE)
				.filter(otp -> otp.getExpiresAt().isAfter(Instant.now()))
				.map(otp -> OtpResponse.builder()
						.otpId(otp.getId())
						.expiresAt(otp.getExpiresAt())
						.message("OTP đã được gửi đến số " + phone)
						.build())
				.orElseGet(() -> otpService.generateOtp(phone, REFERENCE_TYPE, userId));
	}

	public OtpVerifyResult verifyOtp(long userId, String phone, String otpCode) {
		assertPhoneAvailableForUser(userId, phone);
		return otpService.verifyOtp(phone, otpCode, REFERENCE_TYPE, userId);
	}

	public OtpResponse resendOtp(long userId, String phone) {
		assertPhoneAvailableForUser(userId, phone);
		otpService.findPendingOtp(phone, REFERENCE_TYPE)
				.ifPresent(otp -> otpService.invalidateOtp(otp.getId()));
		return otpService.generateOtp(phone, REFERENCE_TYPE, userId);
	}

	private void assertPhoneAvailableForUser(long userId, String phone) {
		if (userRepository.existsByPhoneIgnoreCaseAndDeletedFalseAndIdNot(phone, userId)) {
			throw new BusinessException(ErrorCode.STAFF_PHONE_EXISTS, "Số điện thoại đã được sử dụng.");
		}
	}
}
