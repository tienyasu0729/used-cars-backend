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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// Chỉ ghi khi Approved / Rejected — map TransferApprovalHistory (approved_by, acted_at).
@Getter
@Setter
@Entity
@Table(name = "TransferApprovalHistory")
public class TransferApprovalHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "transfer_id", nullable = false)
	private TransferRequest transfer;

	@Column(name = "approved_by", nullable = false)
	private Long approvedBy;

	@Column(nullable = false, length = 20)
	private String action;

	@Column(length = 500)
	private String note;

	@Column(name = "acted_at", nullable = false, updatable = false)
	private Instant actedAt;

	@PrePersist
	void onCreate() {
		if (actedAt == null) {
			actedAt = Instant.now();
		}
	}

}
