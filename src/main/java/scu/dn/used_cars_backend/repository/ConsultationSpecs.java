package scu.dn.used_cars_backend.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import scu.dn.used_cars_backend.entity.Consultation;
import scu.dn.used_cars_backend.entity.Vehicle;

// Điều kiện lọc danh sách phiếu tư vấn theo role (Sprint 9).
public final class ConsultationSpecs {

	private ConsultationSpecs() {
	}

	/** Staff/Manager: chỉ phiếu có xe thuộc đúng chi nhánh. */
	public static Specification<Consultation> vehicleBranchId(int branchId) {
		return (root, query, cb) -> {
			Join<Consultation, Vehicle> v = root.join("vehicle", JoinType.INNER);
			return cb.equal(v.get("branch").get("id"), branchId);
		};
	}

	public static Specification<Consultation> statusEqualsIgnoreCase(String status) {
		if (status == null || status.isBlank()) {
			return (root, query, cb) -> cb.conjunction();
		}
		String s = status.trim().toLowerCase();
		return (root, query, cb) -> cb.equal(cb.lower(root.get("status")), s);
	}

	public static Specification<Consultation> priorityEqualsIgnoreCase(String priority) {
		if (priority == null || priority.isBlank()) {
			return (root, query, cb) -> cb.conjunction();
		}
		String p = priority.trim().toLowerCase();
		return (root, query, cb) -> cb.equal(cb.lower(root.get("priority")), p);
	}

	/** Admin: lọc có/không gắn xe (null = bỏ qua). */
	public static Specification<Consultation> hasVehicle(Boolean hasVehicle) {
		if (hasVehicle == null) {
			return (root, query, cb) -> cb.conjunction();
		}
		if (Boolean.TRUE.equals(hasVehicle)) {
			return (root, query, cb) -> cb.isNotNull(root.get("vehicle"));
		}
		return (root, query, cb) -> cb.isNull(root.get("vehicle"));
	}
}
