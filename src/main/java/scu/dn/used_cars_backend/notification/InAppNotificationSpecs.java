package scu.dn.used_cars_backend.notification;

import org.springframework.data.jpa.domain.Specification;

import scu.dn.used_cars_backend.entity.InAppNotification;

import jakarta.persistence.criteria.JoinType;

public final class InAppNotificationSpecs {

	private InAppNotificationSpecs() {
	}

	public static Specification<InAppNotification> forUser(long userId, Boolean readFilter) {
		return (root, q, cb) -> {
			var userJoin = root.join("user", JoinType.INNER);
			var p = cb.equal(userJoin.get("id"), userId);
			if (readFilter != null) {
				p = cb.and(p, cb.equal(root.get("notificationRead"), readFilter));
			}
			return p;
		};
	}
}
