package scu.dn.used_cars_backend.sms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.sms.dto.SmsPendingResponse;
import scu.dn.used_cars_backend.sms.entity.SmsMessage;
import scu.dn.used_cars_backend.sms.repository.SmsMessageRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SmsService {

	private static final String PHONE_REGEX = "^0\\d{9}$";
	private static final int OTP_CONTENT_MAX_LENGTH = 160;
	private static final int GENERAL_CONTENT_MAX_LENGTH = 918;
	private static final int DUPLICATE_WINDOW_SECONDS = 60;
	private static final String OTP_TEMPLATE = "Ma OTP cua ban la: %s. Ma co hieu luc trong 5 phut.";

	private final SmsMessageRepository smsMessageRepository;

	@Transactional
	public SmsMessage createOtpMessage(String phone, String otpCode) {
		String content = String.format(OTP_TEMPLATE, otpCode);
		validatePhone(phone);
		validateOtpContent(content);
		checkDuplicate(phone, content);

		SmsMessage message = new SmsMessage();
		message.setPhone(phone);
		message.setContent(content);
		message.setStatus(SmsMessage.STATUS_PENDING);
		return smsMessageRepository.save(message);
	}

	@Transactional(readOnly = true)
	public List<SmsPendingResponse> getPendingMessages(int limit) {
		int effectiveLimit = Math.min(limit, 10);
		return smsMessageRepository
				.findByStatusOrderByCreatedAtAsc(SmsMessage.STATUS_PENDING, PageRequest.of(0, effectiveLimit))
				.stream()
				.map(this::toSmsPendingResponse)
				.toList();
	}

	@Transactional
	public void confirmMessage(Long id, String status) {
		smsMessageRepository.findById(id).ifPresent(message -> {
			if (!SmsMessage.STATUS_PENDING.equals(message.getStatus())) {
				return;
			}
			message.setStatus(status);
			if (SmsMessage.STATUS_SENT.equals(status)) {
				message.setSentAt(Instant.now());
			}
			smsMessageRepository.save(message);
		});
	}

	private void validatePhone(String phone) {
		if (phone == null || phone.isBlank() || !phone.matches(PHONE_REGEX)) {
			throw new BusinessException(ErrorCode.SMS_VALIDATION_FAILED, "Phone không hợp lệ");
		}
	}

	private void validateOtpContent(String content) {
		if (content == null || content.isBlank()) {
			throw new BusinessException(ErrorCode.SMS_VALIDATION_FAILED, "Content không được rỗng");
		}
		String trimmed = content.trim();
		if (trimmed.length() > GENERAL_CONTENT_MAX_LENGTH) {
			throw new BusinessException(ErrorCode.SMS_VALIDATION_FAILED, "Content vượt quá 918 ký tự");
		}
		if (trimmed.length() > OTP_CONTENT_MAX_LENGTH) {
			throw new BusinessException(ErrorCode.SMS_VALIDATION_FAILED, "Nội dung OTP vượt giới hạn 160 ký tự");
		}
	}

	private void checkDuplicate(String phone, String content) {
		Instant since = Instant.now().minusSeconds(DUPLICATE_WINDOW_SECONDS);
		if (smsMessageRepository.existsByPhoneAndContentAndCreatedAtAfter(phone, content, since)) {
			throw new BusinessException(ErrorCode.SMS_DUPLICATE, "Tin nhắn trùng lặp");
		}
	}

	private SmsPendingResponse toSmsPendingResponse(SmsMessage message) {
		return SmsPendingResponse.builder()
				.id(message.getId())
				.phone(message.getPhone())
				.content(message.getContent())
				.createdAt(message.getCreatedAt())
				.build();
	}
}
