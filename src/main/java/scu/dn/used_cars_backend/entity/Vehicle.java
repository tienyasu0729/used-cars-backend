package scu.dn.used_cars_backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Entity map bảng Vehicles — tin rao xe; fuel/transmission là chuỗi; quan hệ category, subcategory, branch, ảnh.
@Getter
@Setter
@Entity
@Table(name = "Vehicles")
public class Vehicle {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "listing_id", nullable = false, unique = true, length = 20, updatable = false)
	private String listingId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subcategory_id", nullable = false)
	private Subcategory subcategory;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(precision = 18, scale = 0)
	private BigDecimal price;

	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String description;

	private Integer year;

	@Column(length = 50)
	private String fuel;

	@Column(length = 50)
	private String transmission;

	@Column(nullable = false)
	private Integer mileage = 0;

	@Column(name = "body_style", length = 50)
	private String bodyStyle;

	@Column(length = 100)
	private String origin;

	@Column(name = "posting_date")
	private LocalDate postingDate;

	@Column(nullable = false, length = 20)
	private String status = "Available";

	@Column(name = "is_deleted", nullable = false)
	private boolean deleted = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "created_by")
	private Long createdBy;

	@OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder ASC")
	private List<VehicleImage> images = new ArrayList<>();

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
		if (mileage == null) {
			mileage = 0;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

}
