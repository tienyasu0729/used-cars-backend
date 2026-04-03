package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.notification.InAppNotificationRowDto;
import scu.dn.used_cars_backend.dto.notification.UnreadCountDto;
import scu.dn.used_cars_backend.dto.notification.WsNotificationEvent;
import scu.dn.used_cars_backend.entity.InAppNotification;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.notification.InAppNotificationSpecs;
import scu.dn.used_cars_backend.repository.InAppNotificationRepository;
import scu.dn.used_cars_backend.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class InAppNotificationService {

	private final InAppNotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final RealtimeEventPublisher realtimeEventPublisher;

	public Page<InAppNotificationRowDto> listForUser(long userId, Boolean isRead, int page, int size) {
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		var spec = InAppNotificationSpecs.forUser(userId, isRead);
		return notificationRepository.findAll(spec, pageable).map(InAppNotificationRowDto::from);
	}

	public UnreadCountDto unreadCount(long userId) {
		return new UnreadCountDto(notificationRepository.countByUser_IdAndNotificationReadFalse(userId));
	}

	@Transactional
	public void markRead(long userId, long notificationId) {
		InAppNotification n = notificationRepository.findByIdAndUser_Id(notificationId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
		n.setNotificationRead(true);
		User u = n.getUser();
		realtimeEventPublisher.publishUserInbox(u.getEmail(), new WsNotificationEvent("notifications_updated", n.getId(), n.getType()));
	}

	@Transactional
	public void markAllRead(long userId) {
		User u = userRepository.findByIdAndDeletedFalse(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		notificationRepository.markAllReadForUser(userId);
		realtimeEventPublisher.publishUserInbox(u.getEmail(), new WsNotificationEvent("notifications_updated", null, null));
	}

	@Transactional
	public InAppNotification createNotification(long userId, String type, String title, String body, String link) {
		return createNotification(userId, type, title, body, link, null);
	}

	@Transactional
	public InAppNotification createNotification(long userId, String type, String title, String body, String link,
			Integer systemAnnouncementId) {
		User u = userRepository.findByIdAndDeletedFalse(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		InAppNotification n = new InAppNotification();
		n.setUser(u);
		n.setType(type);
		n.setTitle(title);
		n.setBody(body);
		n.setLink(link);
		n.setNotificationRead(false);
		n.setSystemAnnouncementId(systemAnnouncementId);
		n = notificationRepository.save(n);
		realtimeEventPublisher.publishUserInbox(u.getEmail(), new WsNotificationEvent("notifications_updated", n.getId(), type));
		return n;
	}
}
