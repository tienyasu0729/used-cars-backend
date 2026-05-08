package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// Trạng thái chia đều tư vấn theo chi nhánh (round-robin).
@Getter
@Setter
@Entity
@Table(name = "ConsultationRoutingStates")
public class ConsultationRoutingState {

	@Id
	@Column(name = "branch_id", nullable = false)
	private Integer branchId;

	@Column(name = "last_assigned_user_id")
	private Long lastAssignedUserId;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		if (updatedAt == null) {
			updatedAt = Instant.now();
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
