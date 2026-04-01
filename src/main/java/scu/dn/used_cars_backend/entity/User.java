package scu.dn.used_cars_backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "Users")
public class User extends BaseEntity {

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 255, unique = true)
	private String email;

	@Column(length = 20)
	private String phone;

	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@Column(name = "auth_provider", nullable = false, length = 50)
	private String authProvider = "local";

	@Column(name = "provider_id", length = 255)
	private String providerId;

	@Column(name = "avatar_url", length = 500)
	private String avatarUrl;

	// Địa chỉ liên hệ / nơi ở — cột address (NVARCHAR 500).
	@Column(length = 500)
	private String address;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(length = 20)
	private String gender;

	@Column(nullable = false, length = 20)
	private String status = "active";

	@Column(name = "is_deleted", nullable = false)
	private Boolean deleted = false;

	@OneToMany(mappedBy = "user", cascade = CascadeType.PERSIST, orphanRemoval = false)
	private Set<UserRole> userRoles = new HashSet<>();
}
