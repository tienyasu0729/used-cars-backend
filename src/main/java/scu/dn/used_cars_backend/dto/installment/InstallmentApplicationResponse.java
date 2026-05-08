package scu.dn.used_cars_backend.dto.installment;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class InstallmentApplicationResponse {
	private Long id;
	private Long customerId;
	private String customerName;
	private String customerPhone;
	private Long vehicleId;
	private String vehicleTitle;
	private Long depositId;
	private Long preDepositId;
	private String bankLoanId;

	private String fullName;
	private String identityNumber;
	private String phoneNumber;
	private String email;
	private LocalDate dob;
	private LocalDate identityIssuedDate;
	private String identityIssuedPlace;
	private String permanentAddress;
	private String currentAddress;

	private String employmentType;
	private String companyName;
	private String jobTitle;
	private String workDuration;
	private String salaryMethod;
	private String businessName;
	private String businessType;
	private String businessDuration;
	private BigDecimal monthlyIncome;
	private BigDecimal monthlyExpenses;
	private BigDecimal existingLoans;
	private Integer dependentsCount;

	private BigDecimal vehiclePrice;
	private BigDecimal prepaymentPercent;
	private BigDecimal prepaymentAmount;
	private BigDecimal loanAmount;
	private Integer loanTermMonths;
	private String repaymentMethod;
	private String bankCode;
	private Boolean requestPreDeposit;

	private Boolean agreedTerms;
	private Boolean agreedPrivacy;
	private String signatureUrl;
	private LocalDate signedDate;

	private String status;
	private String rejectionReason;
	private String bankPdfUrl;
	private Boolean hasValidDepositForVehicle;
	private Boolean depositProofUploaded;
	private BigDecimal appliedDepositAmount;
	private Boolean canSubmit;
	private String blockingReason;

	private java.util.List<InstallmentDocumentResponse> documents;

	private Instant createdAt;
	private Instant updatedAt;
}
