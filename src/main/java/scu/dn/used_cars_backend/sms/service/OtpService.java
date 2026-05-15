package scu.dn.used_cars_backend.sms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.sms.dto.OtpResponse;
import scu.dn.used_cars_backend.sms.dto.OtpVerifyResult;
import scu.dn.used_cars_backend.sms.entity.OtpVerification;
import scu.dn.used_cars_backend.sms.entity.SmsMessage;
import scu.dn.used_cars_backend.sms.repository.OtpVerificationRepository;
import scu.dn.used_cars_backend.sms.repository.SmsMessageRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9}$");
    private static final Pattern OTP_CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final int OTP_TTL_SECONDS = 300;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 900;
    private static final int RATE_LIMIT_MAX = 3;
    private static final int HOURLY_LIMIT_WINDOW_SECONDS = 3600;
    private static final int HOURLY_LIMIT_MAX = 5;
    private static final String OTP_MESSAGE_TEMPLATE = "Ma OTP cua ban la: %s. Ma co hieu luc trong 5 phut.";

    private final SecureRandom secureRandom = new SecureRandom();
    private final OtpVerificationRepository otpVerificationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    @Transactional
    public OtpResponse generateOtp(String phone, String referenceType, Long referenceId) {
        validatePhone(phone);
        checkDuplicateOtp(phone, referenceType);
        checkRateLimit(phone);

        String otpCode = generateOtpCode();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(OTP_TTL_SECONDS);

        OtpVerification otp = new OtpVerification();
        otp.setPhone(phone);
        otp.setOtpCode(otpCode);
        otp.setReferenceType(referenceType);
        otp.setReferenceId(referenceId);
        otp.setStatus(OtpVerification.STATUS_PENDING);
        otp.setExpiresAt(expiresAt);
        otp = otpVerificationRepository.save(otp);

        createSmsMessage(phone, otpCode);
        recordRateLimit(phone);

        return OtpResponse.builder()
                .otpId(otp.getId())
                .expiresAt(expiresAt)
                .message("OTP đã được gửi đến số " + phone)
                .build();
    }

    @Transactional
    public OtpVerifyResult verifyOtp(String phone, String otpCode, String referenceType, Long referenceId) {
        if (otpCode == null || !OTP_CODE_PATTERN.matcher(otpCode).matches()) {
            throw new BusinessException(ErrorCode.OTP_INVALID_FORMAT, "Mã OTP phải gồm đúng 6 chữ số.");
        }

        OtpVerification otp = otpVerificationRepository
                .findTopByPhoneAndReferenceTypeOrderByCreatedAtDesc(phone, referenceType)
                .orElse(null);

        if (otp == null) {
            throw new BusinessException(ErrorCode.OTP_INVALID_CODE, "Mã OTP không hợp lệ.");
        }

        if (OtpVerification.STATUS_VERIFIED.equals(otp.getStatus())) {
            throw new BusinessException(ErrorCode.OTP_ALREADY_VERIFIED, "Mã OTP đã được sử dụng.");
        }

        if (OtpVerification.STATUS_EXHAUSTED.equals(otp.getStatus())) {
            throw new BusinessException(ErrorCode.OTP_EXHAUSTED, "Đã vượt quá số lần thử. Vui lòng yêu cầu mã mới.");
        }

        if (otp.getExpiresAt().isBefore(Instant.now())) {
            otp.setStatus(OtpVerification.STATUS_EXPIRED);
            otpVerificationRepository.save(otp);
            throw new BusinessException(ErrorCode.OTP_EXPIRED, "Mã OTP đã hết hạn.");
        }

        if (!OtpVerification.STATUS_PENDING.equals(otp.getStatus())) {
            throw new BusinessException(ErrorCode.OTP_INVALID_CODE, "Mã OTP không hợp lệ.");
        }

        if (otp.getOtpCode().equals(otpCode)) {
            otp.setStatus(OtpVerification.STATUS_VERIFIED);
            otp.setVerifiedAt(Instant.now());
            otpVerificationRepository.save(otp);
            return OtpVerifyResult.builder()
                    .otpId(otp.getId())
                    .phone(otp.getPhone())
                    .referenceType(otp.getReferenceType())
                    .referenceId(otp.getReferenceId())
                    .verifiedAt(otp.getVerifiedAt())
                    .build();
        }

        otp.setAttempts(otp.getAttempts() + 1);
        if (otp.getAttempts() >= otp.getMaxAttempts()) {
            otp.setStatus(OtpVerification.STATUS_EXHAUSTED);
            otpVerificationRepository.save(otp);
            throw new BusinessException(ErrorCode.OTP_EXHAUSTED, "Đã vượt quá số lần thử. Vui lòng yêu cầu mã mới.");
        }

        otpVerificationRepository.save(otp);
        int remaining = otp.getMaxAttempts() - otp.getAttempts();
        throw new BusinessException(ErrorCode.OTP_INVALID_CODE,
                "Mã OTP không khớp. Còn " + remaining + " lần thử.");
    }

    public Optional<OtpVerification> findPendingOtp(String phone, String referenceType) {
        return otpVerificationRepository
                .findTopByPhoneAndReferenceTypeAndStatusOrderByCreatedAtDesc(phone, referenceType, OtpVerification.STATUS_PENDING);
    }

    @Transactional
    public void invalidateOtp(Long otpId) {
        otpVerificationRepository.findById(otpId).ifPresent(otp -> {
            if (OtpVerification.STATUS_PENDING.equals(otp.getStatus())) {
                otp.setStatus(OtpVerification.STATUS_EXPIRED);
                otpVerificationRepository.save(otp);
            }
        });
    }

    private void validatePhone(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(ErrorCode.OTP_INVALID_FORMAT, "Số điện thoại không hợp lệ.");
        }
    }

    private void checkDuplicateOtp(String phone, String referenceType) {
        Optional<OtpVerification> existing = otpVerificationRepository
                .findTopByPhoneAndReferenceTypeAndStatusOrderByCreatedAtDesc(phone, referenceType, OtpVerification.STATUS_PENDING);
        if (existing.isPresent() && existing.get().getExpiresAt().isAfter(Instant.now())) {
            long remainingSeconds = Duration.between(Instant.now(), existing.get().getExpiresAt()).getSeconds();
            throw new BusinessException(ErrorCode.OTP_ALREADY_EXISTS,
                    "OTP chưa hết hạn. Vui lòng chờ " + remainingSeconds + " giây.");
        }
    }

    private void checkRateLimit(String phone) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }

        Instant now = Instant.now();
        long nowMillis = now.toEpochMilli();

        String rateKey = "otp:rate:" + phone;
        ZSetOperations<String, String> zOps = redis.opsForZSet();
        zOps.removeRangeByScore(rateKey, 0, nowMillis - (RATE_LIMIT_WINDOW_SECONDS * 1000L));
        Long rateCount = zOps.zCard(rateKey);
        if (rateCount != null && rateCount >= RATE_LIMIT_MAX) {
            throw new BusinessException(ErrorCode.OTP_RATE_LIMITED,
                    "Vượt giới hạn gửi OTP. Vui lòng thử lại sau.");
        }

        String hourlyKey = "otp:hourly:" + phone;
        zOps.removeRangeByScore(hourlyKey, 0, nowMillis - (HOURLY_LIMIT_WINDOW_SECONDS * 1000L));
        Long hourlyCount = zOps.zCard(hourlyKey);
        if (hourlyCount != null && hourlyCount >= HOURLY_LIMIT_MAX) {
            throw new BusinessException(ErrorCode.OTP_RATE_LIMITED,
                    "Đã vượt giới hạn gửi OTP trong 1 giờ. Vui lòng thử lại sau.");
        }
    }

    private void recordRateLimit(String phone) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }

        long nowMillis = Instant.now().toEpochMilli();
        String member = String.valueOf(nowMillis);

        String rateKey = "otp:rate:" + phone;
        redis.opsForZSet().add(rateKey, member, nowMillis);
        redis.expire(rateKey, Duration.ofSeconds(RATE_LIMIT_WINDOW_SECONDS));

        String hourlyKey = "otp:hourly:" + phone;
        redis.opsForZSet().add(hourlyKey, member, nowMillis);
        redis.expire(hourlyKey, Duration.ofSeconds(HOURLY_LIMIT_WINDOW_SECONDS));
    }

    private String generateOtpCode() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private void createSmsMessage(String phone, String otpCode) {
        String content = String.format(OTP_MESSAGE_TEMPLATE, otpCode);
        SmsMessage sms = new SmsMessage();
        sms.setPhone(phone);
        sms.setContent(content);
        sms.setStatus(SmsMessage.STATUS_PENDING);
        smsMessageRepository.save(sms);
    }
}
