package scu.dn.used_cars_backend.dto.installment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SaveInstallmentApplicationRequest {
	@NotNull(message = "Vehicle ID là bắt buộc")
	private Long vehicleId;

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
}
