package scu.dn.used_cars_backend.entity;

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

@Entity
@Table(name = "InstallmentDocuments")
@Getter
@Setter
public class InstallmentDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "application_id", nullable = false)
	private InstallmentApplication application;

	@Column(name = "document_type", nullable = false, length = 50)
	private String documentType;

	@Column(name = "document_url", nullable = false, length = 1000)
	private String documentUrl;

	@Column(name = "original_file_name", length = 255)
	private String originalFileName;

	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private Instant uploadedAt;

	@PrePersist
	protected void onPersist() {
		if (uploadedAt == null) {
			uploadedAt = Instant.now();
		}
	}
}
