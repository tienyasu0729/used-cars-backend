package scu.dn.used_cars_backend.repository.spec;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;

/**
 * Điều kiện lọc danh sách user cho Admin (GET /admin/users).
 */
public final class UserAdminSpecs {

	private UserAdminSpecs() {
	}

	public static Specification<User> notDeleted() {
		return (root, query, cb) -> cb.isFalse(root.get("deleted"));
	}

	/** Subquery để lọc role — tránh join collection + distinct làm phân trang trong RAM. */
	public static Specification<User> hasRoleName(String roleName) {
		return (root, query, cb) -> {
			Subquery<Long> sq = query.subquery(Long.class);
			Root<UserRole> ur = sq.from(UserRole.class);
			sq.select(ur.get("user").get("id"));
			sq.where(cb.equal(ur.get("role").get("name"), roleName));
			return cb.in(root.get("id")).value(sq);
		};
	}

	public static Specification<User> statusEqualsDb(String dbStatus) {
		return (root, query, cb) -> cb.equal(cb.lower(root.get("status")), dbStatus.toLowerCase());
	}

	public static Specification<User> searchLike(String rawSearch) {
		String term = "%" + rawSearch.trim().toLowerCase() + "%";
		return (root, query, cb) -> cb.or(
				cb.like(cb.lower(root.get("name")), term),
				cb.like(cb.lower(root.get("email")), term),
				cb.like(cb.lower(cb.coalesce(root.get("phone"), cb.literal(""))), term));
	}
}
