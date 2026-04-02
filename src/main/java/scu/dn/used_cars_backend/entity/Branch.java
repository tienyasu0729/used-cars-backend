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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

// Entity map bảng Branches — chi nhánh bán xe; manager_id trỏ User quản lý (dùng cho phân quyền manager).
@Getter
@Setter
@Entity
@Table(name = "Branches")
public class Branch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, length = 500)
	private String address;

	@Column(length = 20)
	private String phone;

	/** JSON mảng URL ảnh showroom (NVARCHAR MAX). */
	@Column(name = "showroom_image_urls", columnDefinition = "NVARCHAR(MAX)")
	private String showroomImageUrlsJson;

	@Column(precision = 10, scale = 7)
	private BigDecimal lat;

	@Column(precision = 10, scale = 7)
	private BigDecimal lng;

	@Column(nullable = false, length = 20)
	private String status = "active";

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "manager_id")
	private User manager;

	@Column(name = "is_deleted", nullable = false)
	private boolean deleted = false;

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
