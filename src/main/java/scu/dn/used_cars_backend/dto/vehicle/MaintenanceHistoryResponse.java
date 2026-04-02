package scu.dn.used_cars_backend.dto.vehicle;

// DTO trả về bản ghi bảo dưỡng xe.

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class MaintenanceHistoryResponse {

	private Long id;
	private Long vehicleId;
	private LocalDate maintenanceDate;
	private String description;
	private BigDecimal cost;
	private String performedBy;
	private Instant createdAt;
}
