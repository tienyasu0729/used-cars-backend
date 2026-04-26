package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "InstallmentStatusHistory")
@Getter
@Setter
public class InstallmentStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "application_id", nullable = false)
	private InstallmentApplication application;

	@Enumerated(EnumType.STRING)
	@Column(name = "old_status", length = 30)
	private InstallmentApplication.Status oldStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_status", nullable = false, length = 30)
	private InstallmentApplication.Status newStatus;

	@Column(name = "note", length = 1000)
	private String note;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "changed_by")
	private User changedBy;

	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	@PrePersist
	protected void onPersist() {
		if (changedAt == null) {
			changedAt = Instant.now();
		}
	}
}
