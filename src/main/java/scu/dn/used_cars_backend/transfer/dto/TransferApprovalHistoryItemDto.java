package scu.dn.used_cars_backend.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferApprovalHistoryItemDto {

	private Long approvedBy;
	private String approvedByName;
	private String action;
	private String note;
	private Instant actedAt;

}
