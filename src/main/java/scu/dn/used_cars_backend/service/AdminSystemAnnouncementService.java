package scu.dn.used_cars_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.admin.SystemAnnouncementDtos.AdminAnnouncementRowDto;
import scu.dn.used_cars_backend.dto.admin.SystemAnnouncementDtos.CreateSystemAnnouncementRequest;
import scu.dn.used_cars_backend.dto.admin.SystemAnnouncementDtos.UpdateSystemAnnouncementRequest;
import scu.dn.used_cars_backend.dto.notification.WsNotificationEvent;
import scu.dn.used_cars_backend.entity.SystemAnnouncement;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.InAppNotificationRepository;
import scu.dn.used_cars_backend.repository.SystemAnnouncementRepository;
import scu.dn.used_cars_backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSystemAnnouncementService {

	public static final String AUD_ALL_CUSTOMERS = "ALL_CUSTOMERS";
	public static final String AUD_STAFF = "STAFF_AND_MANAGERS";
	public static final String AUD_SPECIFIC = "SPECIFIC_USERS";

	private final SystemAnnouncementRepository announcementRepository;
	private final InAppNotificationRepository inAppNotificationRepository;
	private final UserRepository userRepository;
	private final InAppNotificationService inAppNotificationService;
	private final RealtimeEventPublisher realtimeEventPublisher;
	private final ObjectMapper objectMapper;
	private final ObjectProvider<JavaMailSender> javaMailSender;
	@Value("${app.mail.from:}")
	private String mailFromProp;
	@Value("${spring.mail.username:}")
	private String springMailUsername;

	public Page<AdminAnnouncementRowDto> list(int page, int size) {
		var pg = announcementRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
		return pg.map(a -> AdminAnnouncementRowDto.from(a, parseTargets(a.getTargetUserIds())));
	}

	@Transactional
	public Integer create(long adminUserId, CreateSystemAnnouncementRequest req) {
		validateAudience(req.audience(), req.targetUserIds());
		SystemAnnouncement a = new SystemAnnouncement();
		applyCommon(a, req.title(), req.body(), req.notifKind(), req.audience(), req.targetUserIds(), req.sendEmail(), req.published());
		a.setCreatedBy(userRepository.getReferenceById(adminUserId));
		a = announcementRepository.save(a);
		realtimeEventPublisher.publishAdminActivity(
				new WsNotificationEvent("admin_notifications_updated", annId(a.getId()), a.getNotifKind()));
		if (a.isPublished()) {
			publishFanoutAndEmail(a);
		}
		return a.getId();
	}

	@Transactional
	public void update(int id, long adminUserId, UpdateSystemAnnouncementRequest req) {
		validateAudience(req.audience(), req.targetUserIds());
		SystemAnnouncement a = announcementRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
		boolean wasPublished = a.isPublished();
		applyCommon(a, req.title(), req.body(), req.notifKind(), req.audience(), req.targetUserIds(), req.sendEmail(), req.published());
		announcementRepository.save(a);
		realtimeEventPublisher.publishAdminActivity(
				new WsNotificationEvent("admin_notifications_updated", annId(a.getId()), a.getNotifKind()));
		if (a.isPublished() && !wasPublished) {
			publishFanoutAndEmail(a);
		}
	}

	@Transactional
	public void delete(int id) {
		if (!announcementRepository.existsById(id)) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
		}
		List<String> recipientEmails = List.copyOf(inAppNotificationRepository.findDistinctUserEmailsForSystemAnnouncement(id));
		announcementRepository.deleteById(id);
		Runnable pushWs = () -> {
			for (String email : recipientEmails) {
				if (email != null && !email.isBlank()) {
					realtimeEventPublisher.publishUserInbox(email.trim(), new WsNotificationEvent("notifications_updated", null, null));
				}
			}
			realtimeEventPublisher.publishAdminActivity(new WsNotificationEvent("admin_notifications_updated", (long) id, null));
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					pushWs.run();
				}
			});
		} else {
			pushWs.run();
		}
	}

	@Transactional
	public void publish(int id) {
		SystemAnnouncement a = announcementRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
		if (a.isPublished()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Đã phát hành.");
		}
		a.setPublished(true);
		announcementRepository.save(a);
		publishFanoutAndEmail(a);
		realtimeEventPublisher.publishAdminActivity(
				new WsNotificationEvent("admin_notifications_updated", annId(a.getId()), a.getNotifKind()));
	}

	private static Long annId(Integer id) {
		return id == null ? null : id.longValue();
	}

	private void applyCommon(SystemAnnouncement a, String title, String body, String notifKind, String audience,
			List<Long> targetUserIds, boolean sendEmail, boolean published) {
		a.setTitle(title);
		a.setBody(body);
		a.setNotifKind(notifKind);
		a.setAudience(audience);
		a.setTargetUserIds(serializeTargets(targetUserIds));
		a.setSendEmail(sendEmail);
		a.setPublished(published);
	}

	private void validateAudience(String audience, List<Long> targetUserIds) {
		if (AUD_SPECIFIC.equals(audience) && (targetUserIds == null || targetUserIds.isEmpty())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SPECIFIC_USERS cần targetUserIds.");
		}
	}

	private void publishFanoutAndEmail(SystemAnnouncement a) {
		List<User> recipients = resolveRecipients(a);
		String notifType = "System";
		for (User u : recipients) {
			inAppNotificationService.createNotification(u.getId(), notifType, a.getTitle(), a.getBody(), null, a.getId());
		}
		boolean wantMail = a.isSendEmail() || "email".equalsIgnoreCase(a.getNotifKind());
		if (!wantMail) {
			return;
		}
		JavaMailSender sender = javaMailSender.getIfAvailable();
		if (sender == null) {
			a.setEmailLastError("SMTP chưa cấu hình");
			announcementRepository.save(a);
			return;
		}
		String from = !mailFromProp.isBlank() ? mailFromProp : springMailUsername;
		if (from == null || from.isBlank()) {
			a.setEmailLastError("MAIL_FROM / spring.mail.username trống");
			announcementRepository.save(a);
			return;
		}
		try {
			for (User u : recipients) {
				if (u.getEmail() == null || u.getEmail().isBlank()) {
					continue;
				}
				MimeMessage mm = sender.createMimeMessage();
				MimeMessageHelper h = new MimeMessageHelper(mm, false, "UTF-8");
				h.setFrom(from);
				h.setTo(u.getEmail());
				h.setSubject(a.getTitle());
				h.setText(a.getBody(), false);
				sender.send(mm);
			}
			a.setEmailSentAt(Instant.now());
			a.setEmailLastError(null);
		} catch (Exception e) {
			a.setEmailLastError(truncate(e.getMessage(), 500));
		}
		announcementRepository.save(a);
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return null;
		}
		return s.length() <= max ? s : s.substring(0, max);
	}

	private List<User> resolveRecipients(SystemAnnouncement a) {
		return switch (a.getAudience()) {
			case AUD_ALL_CUSTOMERS -> userRepository.findActiveCustomersWithRoles();
			case AUD_STAFF -> userRepository.findActiveStaffAndManagersWithRoles();
			case AUD_SPECIFIC -> {
				List<Long> ids = parseTargets(a.getTargetUserIds());
				yield ids.isEmpty() ? List.of() : userRepository.findActiveByIdIn(ids);
			}
			default -> List.of();
		};
	}

	private String serializeTargets(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(ids);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private List<Long> parseTargets(String json) {
		if (json == null || json.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<List<Long>>() {
			});
		} catch (JsonProcessingException e) {
			return new ArrayList<>();
		}
	}
}
