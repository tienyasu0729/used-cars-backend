package scu.dn.used_cars_backend.transfer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import scu.dn.used_cars_backend.entity.Vehicle;

import java.time.Instant;

// Yêu cầu điều chuyển xe — map bảng TransferRequests (CK status 4 giá trị).
@Getter
@Setter
@Entity
@Table(name = "TransferRequests")
public class TransferRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@Column(name = "from_branch_id", nullable = false)
	private Integer fromBranchId;

	@Column(name = "to_branch_id", nullable = false)
	private Integer toBranchId;

	@Column(name = "requested_by", nullable = false)
	private Long requestedBy;

	@Column(nullable = false, length = 20)
	private String status = "Pending";

	@Column(length = 500)
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

}
