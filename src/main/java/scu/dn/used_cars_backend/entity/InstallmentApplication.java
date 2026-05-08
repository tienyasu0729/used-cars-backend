package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "InstallmentApplications")
@Getter
@Setter
public class InstallmentApplication extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", nullable = false)
	private User customer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "deposit_id")
	private Deposit deposit;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pre_deposit_id")
	private Deposit preDeposit;

	@Column(name = "bank_loan_id", length = 100)
	private String bankLoanId;

	@Column(name = "bank_code", length = 50)
	private String bankCode;

	// Personal Info (Draft / Snapshot)
	@Column(name = "full_name", length = 100)
	private String fullName;

	@Column(name = "identity_number", length = 20)
	private String identityNumber;

	@Column(name = "phone_number", length = 20)
	private String phoneNumber;

	@Column(name = "email", length = 255)
	private String email;

	@Column(name = "dob")
	private LocalDate dob;

	@Column(name = "identity_issued_date")
	private LocalDate identityIssuedDate;

	@Column(name = "identity_issued_place", length = 200)
	private String identityIssuedPlace;

	@Column(name = "permanent_address", length = 500)
	private String permanentAddress;

	@Column(name = "current_address", length = 500)
	private String currentAddress;

	@Column(name = "employment_type", length = 100)
	private String employmentType;

	@Column(name = "company_name", length = 200)
	private String companyName;

	@Column(name = "job_title", length = 100)
	private String jobTitle;

	@Column(name = "work_duration", length = 50)
	private String workDuration;

	@Column(name = "salary_method", length = 50)
	private String salaryMethod;

	@Column(name = "business_name", length = 200)
	private String businessName;

	@Column(name = "business_type", length = 100)
	private String businessType;

	@Column(name = "business_duration", length = 50)
	private String businessDuration;

	@Column(name = "monthly_income")
	private BigDecimal monthlyIncome;

	@Column(name = "monthly_expenses")
	private BigDecimal monthlyExpenses;

	@Column(name = "existing_loans")
	private BigDecimal existingLoans;

	@Column(name = "dependents_count")
	private Integer dependentsCount;

	@Column(name = "vehicle_price")
	private BigDecimal vehiclePrice;

	@Column(name = "prepayment_percent", precision = 5, scale = 2)
	private BigDecimal prepaymentPercent;

	@Column(name = "prepayment_amount")
	private BigDecimal prepaymentAmount;

	@Column(name = "loan_amount")
	private BigDecimal loanAmount;

	@Column(name = "loan_term_months")
	private Integer loanTermMonths;

	@Column(name = "repayment_method", length = 50)
	private String repaymentMethod;

	@Column(name = "request_pre_deposit")
	private Boolean requestPreDeposit;

	@Column(name = "agreed_terms")
	private Boolean agreedTerms;

	@Column(name = "agreed_privacy")
	private Boolean agreedPrivacy;

	@Column(name = "signature_url", length = 1000)
	private String signatureUrl;

	@Column(name = "signed_date")
	private LocalDate signedDate;

	// Status
	public enum Status {
		DRAFT, PENDING_DOCUMENT, BANK_PROCESSING, APPROVED, REJECTED, DEPOSIT_PENDING, DEPOSIT_PAID, COMPLETED, CANCELLED
	}

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private Status status = Status.DRAFT;

	@Column(name = "rejection_reason", length = 1000)
	private String rejectionReason;

	@Column(name = "bank_pdf_url", length = 1000)
	private String bankPdfUrl;

	@OneToMany(mappedBy = "application", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
	private List<InstallmentDocument> documents = new ArrayList<>();

	@OneToMany(mappedBy = "application", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
	private List<InstallmentStatusHistory> statusHistories = new ArrayList<>();
}
