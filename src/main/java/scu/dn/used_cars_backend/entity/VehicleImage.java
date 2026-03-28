package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Entity map bảng VehicleImages — URL ảnh + thứ tự + cờ ảnh đại diện.
@Getter
@Setter
@Entity
@Table(name = "VehicleImages")
public class VehicleImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder = 0;

	@Column(name = "is_primary", nullable = false)
	private boolean primaryImage = false;

}
