package scu.dn.used_cars_backend.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.dto.CreateDocumentSessionRequest;
import scu.dn.used_cars_backend.booking.dto.DocumentSessionResponse;
import scu.dn.used_cars_backend.booking.entity.Booking;
import scu.dn.used_cars_backend.booking.entity.DocumentSession;
import scu.dn.used_cars_backend.audit.AuditLogWriter;
import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.booking.repository.DocumentSessionRepository;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.notification.WsNotificationEvent;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.service.RealtimeEventPublisher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSessionService {

	private static final Set<String> VALID_PURPOSES = Set.of("CCCD", "LICENSE", "SIGNATURE");
	private static final long SESSION_EXPIRY_MINUTES = 5;

	@Value("${app.payment.frontend-base-url:}")
	private String frontendBaseUrl;

	private final DocumentSessionRepository sessionRepository;
	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;
	private final RealtimeEventPublisher realtimeEventPublisher;
	private final AuditLogWriter auditLogWriter;

	@Transactional
	public DocumentSessionResponse createSession(CreateDocumentSessionRequest req, long userId, String clientIp, String userLabel) {
		if (!VALID_PURPOSES.contains(req.getPurpose())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Purpose không hợp lệ: " + req.getPurpose());
		}
		Booking b = bookingRepository.findWithDetailsById(req.getBookingId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy lịch hẹn."));
		if (!b.getCustomerId().equals(userId)) {
			throw new BusinessException(ErrorCode.BOOKING_ACCESS_DENIED, "Không có quyền thao tác lịch hẹn này.");
		}

		String sessionId = UUID.randomUUID().toString().replace("-", "");
		String oneTimeToken = UUID.randomUUID().toString().replace("-", "");
		String tokenHash = sha256(oneTimeToken);

		DocumentSession ds = new DocumentSession();
		ds.setSessionId(sessionId);
		ds.setTokenHash(tokenHash);
		ds.setBooking(b);
		ds.setUserId(userId);
		ds.setPurpose(req.getPurpose());
		ds.setStatus("WAITING");
		ds.setExpiresAt(Instant.now().plus(SESSION_EXPIRY_MINUTES, ChronoUnit.MINUTES));
		sessionRepository.save(ds);

		auditLogWriter.persist(userId, userLabel, "Contract", "CREATE_DOC_SESSION",
				"bookingId=" + req.getBookingId() + ", sessionId=" + sessionId + ", purpose=" + req.getPurpose(),
				clientIp);

		String baseUrl = frontendBaseUrl != null && !frontendBaseUrl.isBlank()
				? frontendBaseUrl.replaceAll("/+$", "") : "http://localhost:5173";
		String qrUrl = baseUrl + "/m/upload?session=" + sessionId + "&t=" + oneTimeToken;

		return DocumentSessionResponse.builder()
				.sessionId(sessionId)
				.purpose(ds.getPurpose())
				.status(ds.getStatus())
				.qrUrl(qrUrl)
				.expiresAt(ds.getExpiresAt())
				.build();
	}

	@Transactional(readOnly = true)
	public DocumentSessionResponse getSessionPublic(String sessionId, String token) {
		DocumentSession ds = findValidSession(sessionId, token);
		return DocumentSessionResponse.builder()
				.sessionId(ds.getSessionId())
				.purpose(ds.getPurpose())
				.status(ds.getStatus())
				.fileUrl(ds.getFileUrl())
				.expiresAt(ds.getExpiresAt())
				.build();
	}

	@Transactional
	public DocumentSessionResponse uploadDocument(String sessionId, String token, String fileUrl, String clientIp) {
		DocumentSession ds = findValidSession(sessionId, token);
		if ("COMPLETED".equals(ds.getStatus())) {
			throw new BusinessException(ErrorCode.DOCUMENT_SESSION_EXPIRED, "Session đã hoàn tất, không thể upload lại.");
		}

		ds.setFileUrl(fileUrl);
		ds.setStatus("COMPLETED");
		sessionRepository.save(ds);

		auditLogWriter.persist(
				ds.getUserId(),
				userRepository.findById(ds.getUserId()).map(User::getName).orElse(null),
				"Contract",
				"DOCUMENT_UPLOAD",
				"sessionId=" + sessionId + ", bookingId=" + ds.getBooking().getId() + ", purpose=" + ds.getPurpose()
						+ ", fileUrl=" + (fileUrl != null && fileUrl.length() > 200 ? fileUrl.substring(0, 200) + "..." : fileUrl),
				clientIp);

		pushRealtimeUpdate(ds);

		return DocumentSessionResponse.builder()
				.sessionId(ds.getSessionId())
				.purpose(ds.getPurpose())
				.status("COMPLETED")
				.fileUrl(fileUrl)
				.expiresAt(ds.getExpiresAt())
				.build();
	}

	@Transactional(readOnly = true)
	public DocumentSessionResponse pollSession(String sessionId, long userId) {
		DocumentSession ds = sessionRepository.findBySessionId(sessionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_SESSION_NOT_FOUND, "Session không tồn tại."));
		if (!ds.getUserId().equals(userId)) {
			throw new BusinessException(ErrorCode.BOOKING_ACCESS_DENIED, "Không có quyền xem session này.");
		}
		return DocumentSessionResponse.builder()
				.sessionId(ds.getSessionId())
				.purpose(ds.getPurpose())
				.status(ds.getStatus())
				.fileUrl(ds.getFileUrl())
				.expiresAt(ds.getExpiresAt())
				.build();
	}

	@Scheduled(fixedDelay = 60_000)
	@Transactional
	public void expireSessions() {
		int count = sessionRepository.expireOldSessions(Instant.now());
		if (count > 0) {
			log.info("Expired {} document sessions", count);
		}
	}

	private DocumentSession findValidSession(String sessionId, String token) {
		DocumentSession ds = sessionRepository.findBySessionId(sessionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_SESSION_NOT_FOUND, "Session không tồn tại."));
		if ("EXPIRED".equals(ds.getStatus()) || ds.getExpiresAt().isBefore(Instant.now())) {
			ds.setStatus("EXPIRED");
			sessionRepository.save(ds);
			throw new BusinessException(ErrorCode.DOCUMENT_SESSION_EXPIRED, "Session đã hết hạn.");
		}
		String tokenHash = sha256(token);
		if (!tokenHash.equals(ds.getTokenHash())) {
			throw new BusinessException(ErrorCode.DOCUMENT_SESSION_INVALID_TOKEN, "Token không hợp lệ.");
		}
		return ds;
	}

	private void pushRealtimeUpdate(DocumentSession ds) {
		try {
			User user = userRepository.findById(ds.getUserId()).orElse(null);
			if (user == null || user.getEmail() == null) return;
			WsNotificationEvent evt = new WsNotificationEvent("DOCUMENT_UPLOAD", ds.getId(), "DOCUMENT_UPLOAD");
			realtimeEventPublisher.publishUserInbox(user.getEmail(), evt);
		} catch (Exception e) {
			log.warn("Push realtime document session update failed: {}", e.getMessage());
		}
	}

	private static String sha256(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			throw new RuntimeException("SHA-256 not available", e);
		}
	}
}
