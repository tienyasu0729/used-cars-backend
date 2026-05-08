package scu.dn.used_cars_backend.usedcarpurchase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "UsedCarPurchaseRequests")
public class UsedCarPurchaseRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "branch_id", nullable = false)
	private Integer branchId;

	@Column(name = "requested_by", nullable = false)
	private Long requestedBy;

	@Column(name = "requested_by_name", length = 255)
	private String requestedByName;

	@Column(nullable = false, length = 40)
	private String status;

	@Column(name = "requested_purchase_price", nullable = false, precision = 18, scale = 0)
	private BigDecimal requestedPurchasePrice;

	@Column(name = "approved_purchase_price", precision = 18, scale = 0)
	private BigDecimal approvedPurchasePrice;

	@Column(name = "manager_note", columnDefinition = "NVARCHAR(MAX)")
	private String managerNote;

	@Column(name = "admin_note", columnDefinition = "NVARCHAR(MAX)")
	private String adminNote;

	@Column(name = "vehicle_snapshot_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
	private String vehicleSnapshotJson;

	@Column(name = "image_snapshot_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
	private String imageSnapshotJson;

	@Column(name = "valuation_snapshot_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
	private String valuationSnapshotJson;

	@Column(name = "created_vehicle_id")
	private Long createdVehicleId;

	@Column(name = "approved_by")
	private Long approvedBy;

	@Column(name = "approved_by_name", length = 255)
	private String approvedByName;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Column(name = "paid_by")
	private Long paidBy;

	@Column(name = "paid_by_name", length = 255)
	private String paidByName;

	@Column(name = "paid_at")
	private Instant paidAt;

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
		if (status == null || status.isBlank()) {
			status = "PendingApproval";
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
