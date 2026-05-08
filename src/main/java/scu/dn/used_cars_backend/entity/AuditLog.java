package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "AuditLogs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "user_name", length = 100)
	private String userName;

	@Column(nullable = false, length = 100)
	private String action;

	@Column(nullable = false, length = 50)
	private String module;

	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String details;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(name = "[timestamp]", insertable = false, updatable = false)
	private Instant timestamp;
}
