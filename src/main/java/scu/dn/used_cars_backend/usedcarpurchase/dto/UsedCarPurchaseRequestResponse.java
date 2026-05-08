package scu.dn.used_cars_backend.usedcarpurchase.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class UsedCarPurchaseRequestResponse {
	private Long id;
	private Integer branchId;
	private Long requestedBy;
	private String requestedByName;
	private String status;
	private BigDecimal requestedPurchasePrice;
	private BigDecimal approvedPurchasePrice;
	private String managerNote;
	private String adminNote;
	private Long approvedBy;
	private String approvedByName;
	private Instant approvedAt;
	private Long paidBy;
	private String paidByName;
	private Instant paidAt;
	private Long createdVehicleId;
	private Instant createdAt;
	private Instant updatedAt;
	private String vehicleTitle;
	private String primaryImageUrl;
	private Map<String, Object> vehicleSnapshot;
	private List<Map<String, Object>> imageSnapshot;
	private Map<String, Object> valuationSnapshot;
}
