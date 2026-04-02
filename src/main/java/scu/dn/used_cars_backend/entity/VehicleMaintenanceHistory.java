package scu.dn.used_cars_backend.entity;

// Entity map bảng VehicleMaintenanceHistory — lịch sử bảo dưỡng xe.

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "VehicleMaintenanceHistory")
public class VehicleMaintenanceHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@Column(name = "maintenance_date", nullable = false)
	private LocalDate maintenanceDate;

	@Column(name = "description", nullable = false, length = 500)
	private String description;

	@Column(name = "cost", nullable = false)
	private BigDecimal cost;

	@Column(name = "performed_by", length = 200)
	private String performedBy;

	@Column(name = "created_at", nullable = false, updatable = false, insertable = false)
	private Instant createdAt;
}
