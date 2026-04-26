package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "UserRoles")
public class UserRole {

	@EmbeddedId
	private UserRoleId id = new UserRoleId();

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@MapsId("roleId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id")
	private Role role;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	@PrePersist
	void prePersist() {
		if (assignedAt == null) {
			assignedAt = Instant.now();
		}
	}
}
