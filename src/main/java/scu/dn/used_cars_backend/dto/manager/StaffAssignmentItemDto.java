package scu.dn.used_cars_backend.dto.manager;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class StaffAssignmentItemDto {

	private Long id;
	private Integer branchId;
	private String branchName;
	private LocalDate startDate;
	private LocalDate endDate;
	private boolean active;
}
