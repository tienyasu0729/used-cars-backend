package scu.dn.used_cars_backend.dto.consultation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationListItemDto {

	private long id;
	private Long customerId;
	private String customerName;
	private String customerPhone;
	private Long vehicleId;
	private String vehicleTitle;
	private String message;
	private String status;
	private String priority;
	private Long assignedStaffId;
	private String assignedStaffName;
	private Instant createdAt;
	private Instant updatedAt;
}
