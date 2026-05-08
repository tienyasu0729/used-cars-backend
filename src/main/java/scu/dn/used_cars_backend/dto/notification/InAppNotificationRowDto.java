package scu.dn.used_cars_backend.dto.notification;

import java.time.Instant;

import scu.dn.used_cars_backend.entity.InAppNotification;

public record InAppNotificationRowDto(long id, String type, String title, String body, String link, boolean read,
		Instant createdAt) {

	public static InAppNotificationRowDto from(InAppNotification n) {
		return new InAppNotificationRowDto(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
				n.isNotificationRead(), n.getCreatedAt());
	}
}
