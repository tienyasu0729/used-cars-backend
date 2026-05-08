package scu.dn.used_cars_backend.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponseDto {

	private Long id;
	private Long vehicleId;
	private String vehicleTitle;
	private String vehicleListingId;
	private Integer fromBranchId;
	private String fromBranchName;
	private Integer toBranchId;
	private String toBranchName;
	private Long requestedBy;
	private String requestedByName;
	private String status;
	private String reason;
	private Instant createdAt;
	private Instant updatedAt;
	private List<TransferApprovalHistoryItemDto> approvalHistory;

}
