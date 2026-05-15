package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.sms.dto.OtpRequest;
import scu.dn.used_cars_backend.sms.dto.OtpResponse;
import scu.dn.used_cars_backend.sms.dto.OtpVerifyRequest;
import scu.dn.used_cars_backend.sms.dto.OtpVerifyResult;
import scu.dn.used_cars_backend.sms.service.OtpService;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/installments/otp")
@RequiredArgsConstructor
public class InstallmentOtpController {

    private static final String REFERENCE_TYPE = "installment";
    private static final int MAX_RESEND = 3;
    private static final int RESEND_INTERVAL_SECONDS = 60;

    private final OtpService otpService;
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    @PostMapping("/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OtpResponse>> requestOtp(
            @Valid @RequestBody OtpRequest request, Authentication auth) {
        AuthenticationDetailsUtils.requireUserId(auth);
        OtpResponse response = otpService.generateOtp(
                request.getPhone(), REFERENCE_TYPE, request.getReferenceId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OtpVerifyResult>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request, Authentication auth) {
        AuthenticationDetailsUtils.requireUserId(auth);
        OtpVerifyResult result = otpService.verifyOtp(
                request.getPhone(), request.getOtpCode(), REFERENCE_TYPE, request.getReferenceId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/resend")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OtpResponse>> resendOtp(
            @Valid @RequestBody OtpRequest request, Authentication auth) {
        AuthenticationDetailsUtils.requireUserId(auth);
        String phone = request.getPhone();
        Long referenceId = request.getReferenceId();
        String sessionKey = buildResendSessionKey(phone, referenceId);

        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            checkResendLimit(redis, sessionKey);
            checkResendInterval(redis, sessionKey);
        }

        invalidateCurrentOtp(phone);

        OtpResponse response = otpService.generateOtp(phone, REFERENCE_TYPE, referenceId);

        if (redis != null) {
            recordResend(redis, sessionKey);
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String buildResendSessionKey(String phone, Long referenceId) {
        return "otp:resend:installment:" + phone + ":" + referenceId;
    }

    private void checkResendLimit(StringRedisTemplate redis, String sessionKey) {
        String countStr = redis.opsForValue().get(sessionKey + ":count");
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        if (count >= MAX_RESEND) {
            throw new BusinessException(ErrorCode.OTP_RESEND_LIMIT_EXCEEDED,
                    "Đã vượt quá số lần gửi lại OTP (tối đa " + MAX_RESEND + " lần).");
        }
    }

    private void checkResendInterval(StringRedisTemplate redis, String sessionKey) {
        String lastResendStr = redis.opsForValue().get(sessionKey + ":last");
        if (lastResendStr != null) {
            Instant lastResend = Instant.ofEpochMilli(Long.parseLong(lastResendStr));
            long elapsed = Duration.between(lastResend, Instant.now()).getSeconds();
            if (elapsed < RESEND_INTERVAL_SECONDS) {
                long remaining = RESEND_INTERVAL_SECONDS - elapsed;
                throw new BusinessException(ErrorCode.OTP_RESEND_TOO_FAST,
                        "Vui lòng chờ " + remaining + " giây trước khi gửi lại OTP.");
            }
        }
    }

    private void invalidateCurrentOtp(String phone) {
        var existing = otpService.findPendingOtp(phone, REFERENCE_TYPE);
        existing.ifPresent(otp -> otpService.invalidateOtp(otp.getId()));
    }

    private void recordResend(StringRedisTemplate redis, String sessionKey) {
        String countKey = sessionKey + ":count";
        String lastKey = sessionKey + ":last";

        redis.opsForValue().increment(countKey);
        redis.expire(countKey, Duration.ofMinutes(30));

        redis.opsForValue().set(lastKey, String.valueOf(Instant.now().toEpochMilli()));
        redis.expire(lastKey, Duration.ofSeconds(RESEND_INTERVAL_SECONDS));
    }
}
