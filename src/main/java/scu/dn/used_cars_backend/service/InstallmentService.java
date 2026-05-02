package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.common.web.HttpServletClientIp;
import scu.dn.used_cars_backend.dto.installment.CreateInstallmentPreDepositRequest;
import scu.dn.used_cars_backend.dto.installment.InstallmentApplicationResponse;
import scu.dn.used_cars_backend.dto.installment.InstallmentDocumentResponse;
import scu.dn.used_cars_backend.dto.installment.InstallmentSubmitEligibilityResponse;
import scu.dn.used_cars_backend.dto.installment.SaveInstallmentApplicationRequest;
import scu.dn.used_cars_backend.dto.sales.CreateDepositRequest;
import scu.dn.used_cars_backend.dto.sales.CreateDepositResponse;
import scu.dn.used_cars_backend.entity.AuditLog;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.InAppNotification;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.InstallmentDocument;
import scu.dn.used_cars_backend.entity.InstallmentStatusHistory;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.AuditLogRepository;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.InAppNotificationRepository;
import scu.dn.used_cars_backend.repository.InstallmentApplicationRepository;
import scu.dn.used_cars_backend.repository.InstallmentDocumentRepository;
import scu.dn.used_cars_backend.repository.InstallmentStatusHistoryRepository;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstallmentService {
	private static final BigDecimal MIN_PREPAY_PERCENT = new BigDecimal("30");
	private static final BigDecimal MAX_PREPAY_PERCENT = new BigDecimal("70");
	private static final BigDecimal PREPAY_AMOUNT_TOLERANCE = new BigDecimal("1000");
	private static final long INSTALLMENT_PRE_DEPOSIT_TIMEOUT_MINUTES = 60;
	private static final List<String> DEPOSIT_LOCK_STATUSES = List.of("Pending", "Confirmed", "AwaitingPayment");
	private static final List<String> DEPOSIT_VALID_STATUSES = List.of("Pending", "Confirmed");
	private static final List<String> ORDER_LOCK_STATUSES = List.of("Pending", "Processing", "Completed");
	private static final Set<InstallmentApplication.Status> SINGLE_CREATE_LOCK_STATUSES = Set.of(
			InstallmentApplication.Status.DRAFT);
	private static final String DEPOSIT_RECEIPT_DOC_TYPE = "DEPOSIT_RECEIPT";
	private static final Set<String> SUPPORTED_BANK_CODES = Set.of(
			"VCB", "TCB", "BIDV", "VPB", "ACB", "MB", "VIB", "SACOMBANK");

	private final InstallmentApplicationRepository applicationRepository;
	private final InstallmentDocumentRepository documentRepository;
	private final UserRepository userRepository;
	private final VehicleRepository vehicleRepository;
	private final CloudinaryDocumentService cloudinaryDocumentService;
	private final BankIntegrationService bankIntegrationService;
	private final InstallmentStatusHistoryRepository statusHistoryRepository;
	private final InAppNotificationRepository notificationRepository;
	private final AuditLogRepository auditLogRepository;
	private final DepositRepository depositRepository;
	private final DepositService depositService;
	private final SalesOrderRepository salesOrderRepository;
	private final InstallmentPaymentCacheService installmentPaymentCacheService;

	@Value("${app.installment.pre-deposit-percent:10}")
	private BigDecimal preDepositPercent;

	@Transactional
	public InstallmentApplicationResponse saveApplication(Long customerId, SaveInstallmentApplicationRequest request) {
		User customer = userRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Khong tim thay nguoi dung."));
		Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Khong tim thay xe."));
		assertVehicleStillAvailableForCustomer(vehicle, customerId);

		var existing = applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
				.filter(a -> a.getVehicle().getId().equals(request.getVehicleId())
						&& a.getStatus() == InstallmentApplication.Status.DRAFT)
				.findFirst();
		if (existing.isPresent()) {
			InstallmentApplication app = existing.get();
			updateApplicationFields(app, request);
			if (request.getStatus() != null) {
				try {
					InstallmentApplication.Status requested = InstallmentApplication.Status.valueOf(request.getStatus().toUpperCase());
					app.setStatus(resolveRequestedStatus(customer.getId(), vehicle.getId(), app, requested));
				} catch (IllegalArgumentException ignored) {
					// keep current status
				}
			}
			InstallmentApplication saved = applicationRepository.save(app);
			if (saved.getStatus() == InstallmentApplication.Status.PENDING_DOCUMENT) {
				notifyStaffAndManagersNewInstallment(saved);
			}
			return mapToResponse(saved);
		}
		assertSingleActiveApplicationPerCustomer(customerId, null);

		InstallmentApplication app = new InstallmentApplication();
		app.setCustomer(customer);
		app.setVehicle(vehicle);
		updateApplicationFields(app, request);
		if (request.getStatus() != null) {
			try {
				InstallmentApplication.Status requested = InstallmentApplication.Status.valueOf(request.getStatus().toUpperCase());
				app.setStatus(resolveRequestedStatus(customer.getId(), vehicle.getId(), app, requested));
			} catch (IllegalArgumentException e) {
				app.setStatus(InstallmentApplication.Status.DRAFT);
			}
		} else {
			app.setStatus(InstallmentApplication.Status.DRAFT);
		}
		InstallmentApplication saved = applicationRepository.save(app);
		if (saved.getStatus() == InstallmentApplication.Status.PENDING_DOCUMENT) {
			notifyStaffAndManagersNewInstallment(saved);
		}
		return mapToResponse(saved);
	}

	@Transactional
	public InstallmentApplicationResponse updateApplication(Long customerId, Long id, SaveInstallmentApplicationRequest request) {
		InstallmentApplication app = applicationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (!app.getCustomer().getId().equals(customerId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Ban khong co quyen sua ho so nay.");
		}
		assertVehicleStillAvailableForCustomer(app.getVehicle(), customerId);
		assertSingleActiveApplicationPerCustomer(customerId, app.getId());
		updateApplicationFields(app, request);
		InstallmentApplication.Status oldStatus = app.getStatus();
		if (request.getStatus() != null) {
			try {
				InstallmentApplication.Status requested = InstallmentApplication.Status.valueOf(request.getStatus().toUpperCase());
				app.setStatus(resolveRequestedStatus(app.getCustomer().getId(), app.getVehicle().getId(), app, requested));
			} catch (IllegalArgumentException ignored) {
				// keep current status
			}
		}
		InstallmentApplication saved = applicationRepository.save(app);
		if (oldStatus != InstallmentApplication.Status.PENDING_DOCUMENT
				&& saved.getStatus() == InstallmentApplication.Status.PENDING_DOCUMENT) {
			notifyStaffAndManagersNewInstallment(saved);
		}
		return mapToResponse(saved);
	}

	@Transactional(readOnly = true)
	public InstallmentApplicationResponse getApplication(Long userId, String role, Long id) {
		InstallmentApplication app = applicationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (!"ADMIN".equals(role) && !"SALESSTAFF".equalsIgnoreCase(role) && !app.getCustomer().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Ban khong co quyen xem ho so nay.");
		}
		return mapToResponse(app);
	}

	@Transactional(readOnly = true)
	public InstallmentApplicationResponse restoreApplicationFromPaymentCache(Long userId, String role, Long applicationId) {
		InstallmentApplicationResponse fallback = getApplication(userId, role, applicationId);
		if (!"CUSTOMER".equalsIgnoreCase(role)) {
			return fallback;
		}
		return installmentPaymentCacheService.getByApplicationId(userId, applicationId).orElse(fallback);
	}

	@Transactional(readOnly = true)
	public InstallmentApplicationResponse restoreApplicationFromDepositCache(Long userId, String role, Long depositId) {
		InstallmentApplication app = applicationRepository.findFirstByPreDepositId(depositId)
				.or(() -> applicationRepository.findFirstByDepositId(depositId))
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so theo khoan coc."));
		InstallmentApplicationResponse fallback = getApplication(userId, role, app.getId());
		if (!"CUSTOMER".equalsIgnoreCase(role)) {
			return fallback;
		}
		return installmentPaymentCacheService.getByDepositId(userId, depositId).orElse(fallback);
	}

	@Transactional(readOnly = true)
	public InstallmentSubmitEligibilityResponse getSubmitEligibility(Long userId, String role, Long id) {
		InstallmentApplication app = applicationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (!"ADMIN".equals(role) && !"SALESSTAFF".equalsIgnoreCase(role) && !app.getCustomer().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Ban khong co quyen xem ho so nay.");
		}
		return buildSubmitEligibility(app, app.getCustomer().getId(), app.getVehicle().getId());
	}

	@Transactional(readOnly = true)
	public List<InstallmentApplicationResponse> getMyApplications(Long customerId) {
		return applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
				.stream().map(this::mapToResponse).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public Page<InstallmentApplicationResponse> searchMyApplications(Long customerId, int page, int size, String statusFilter,
			String keyword) {
		InstallmentApplication.Status status = null;
		if (statusFilter != null && !statusFilter.isBlank()) {
			try {
				status = InstallmentApplication.Status.valueOf(statusFilter.trim().toUpperCase());
			} catch (IllegalArgumentException ignored) {
				status = null;
			}
		}
		String q = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
				Sort.by(Sort.Direction.DESC, "createdAt"));
		return applicationRepository.searchMyApplications(customerId, status, q, pageable).map(this::mapToResponse);
	}

	@Transactional(readOnly = true)
	public List<InstallmentApplicationResponse> getAllApplications(String statusFilter) {
		List<InstallmentApplication> apps;
		if (statusFilter != null && !statusFilter.isBlank()) {
			try {
				InstallmentApplication.Status status = InstallmentApplication.Status.valueOf(statusFilter.toUpperCase());
				apps = applicationRepository.findAll().stream()
						.filter(a -> a.getStatus() == status)
						.sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
						.collect(Collectors.toList());
			} catch (IllegalArgumentException e) {
				apps = applicationRepository.findAll();
			}
		} else {
			apps = applicationRepository.findAll();
		}
		apps.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
		return apps.stream().map(this::mapToResponse).collect(Collectors.toList());
	}

	@Transactional
	public void completeApplication(Long staffId, Long applicationId) {
		InstallmentApplication app = applicationRepository.findById(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (Boolean.TRUE.equals(app.getRequestPreDeposit())) {
			if (app.getStatus() != InstallmentApplication.Status.DEPOSIT_PAID) {
				throw new BusinessException(
						ErrorCode.VALIDATION_FAILED,
						"Ho so yeu cau coc truoc chi duoc hoan tat sau khi da thanh toan coc thanh cong.");
			}
		} else if (app.getStatus() != InstallmentApplication.Status.APPROVED
				&& app.getStatus() != InstallmentApplication.Status.DEPOSIT_PAID) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chi ho so APPROVED hoac DEPOSIT_PAID moi duoc hoan tat.");
		}
		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setStatus(InstallmentApplication.Status.COMPLETED);
		applicationRepository.save(app);
		recordStatusHistory(app, oldStatus, app.getStatus(), "Hoan tat boi Staff #" + staffId);
		try {
			InAppNotification noti = new InAppNotification();
			noti.setUser(app.getCustomer());
			noti.setType("INSTALLMENT");
			noti.setTitle("Ho so tra gop hoan tat");
			noti.setBody("Ho so tra gop #" + app.getId() + " da hoan tat.");
			noti.setLink("/installments/applications/" + app.getId());
			noti.setNotificationRead(false);
			notificationRepository.save(noti);
		} catch (Exception e) {
			log.error("Loi gui notification complete: {}", e.getMessage());
		}
	}

	@Transactional
	public InstallmentApplicationResponse saveApplicationOnBehalf(Long staffId, Long customerId, SaveInstallmentApplicationRequest request) {
		User customer = userRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Khong tim thay khach hang."));
		Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Khong tim thay xe."));
		assertVehicleStillAvailableForCustomer(vehicle, customerId);

		var existing = applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
				.filter(a -> a.getVehicle().getId().equals(request.getVehicleId())
						&& a.getStatus() == InstallmentApplication.Status.DRAFT)
				.findFirst();
		if (existing.isPresent()) {
			InstallmentApplication app = existing.get();
			updateApplicationFields(app, request);
			if (request.getStatus() != null) {
				try {
					InstallmentApplication.Status requested = InstallmentApplication.Status.valueOf(request.getStatus().toUpperCase());
					app.setStatus(resolveRequestedStatus(customer.getId(), vehicle.getId(), app, requested));
				} catch (IllegalArgumentException ignored) {
					// keep current status
				}
			}
			return mapToResponse(applicationRepository.save(app));
		}
		assertSingleActiveApplicationPerCustomer(customerId, null);

		InstallmentApplication app = new InstallmentApplication();
		app.setCustomer(customer);
		app.setVehicle(vehicle);
		updateApplicationFields(app, request);
		if (request.getStatus() != null) {
			try {
				InstallmentApplication.Status requested = InstallmentApplication.Status.valueOf(request.getStatus().toUpperCase());
				app.setStatus(resolveRequestedStatus(customer.getId(), vehicle.getId(), app, requested));
			} catch (IllegalArgumentException e) {
				app.setStatus(InstallmentApplication.Status.DRAFT);
			}
		} else {
			app.setStatus(InstallmentApplication.Status.DRAFT);
		}
		app = applicationRepository.save(app);
		try {
			AuditLog auditLog = new AuditLog();
			auditLog.setUserId(staffId);
			auditLog.setUserName("STAFF");
			auditLog.setModule("INSTALLMENT");
			auditLog.setAction("CREATE_ON_BEHALF");
			auditLog.setDetails("Staff #" + staffId + " tao ho so cho KH #" + customerId + " | AppID: " + app.getId());
			auditLogRepository.save(auditLog);
		} catch (Exception e) {
			log.error("Loi ghi audit log on-behalf: {}", e.getMessage());
		}
		return mapToResponse(app);
	}

	private void updateApplicationFields(InstallmentApplication app, SaveInstallmentApplicationRequest request) {
		if (request.getFullName() != null) app.setFullName(request.getFullName());
		if (request.getIdentityNumber() != null) app.setIdentityNumber(request.getIdentityNumber());
		if (request.getPhoneNumber() != null) app.setPhoneNumber(request.getPhoneNumber());
		if (request.getEmail() != null) app.setEmail(request.getEmail());
		if (request.getDob() != null) app.setDob(request.getDob());
		if (request.getIdentityIssuedDate() != null) app.setIdentityIssuedDate(request.getIdentityIssuedDate());
		if (request.getIdentityIssuedPlace() != null) app.setIdentityIssuedPlace(request.getIdentityIssuedPlace());
		if (request.getPermanentAddress() != null) app.setPermanentAddress(request.getPermanentAddress());
		if (request.getCurrentAddress() != null) app.setCurrentAddress(request.getCurrentAddress());
		if (request.getEmploymentType() != null) app.setEmploymentType(request.getEmploymentType());
		if (request.getCompanyName() != null) app.setCompanyName(request.getCompanyName());
		if (request.getJobTitle() != null) app.setJobTitle(request.getJobTitle());
		if (request.getWorkDuration() != null) app.setWorkDuration(request.getWorkDuration());
		if (request.getSalaryMethod() != null) app.setSalaryMethod(request.getSalaryMethod());
		if (request.getBusinessName() != null) app.setBusinessName(request.getBusinessName());
		if (request.getBusinessType() != null) app.setBusinessType(request.getBusinessType());
		if (request.getBusinessDuration() != null) app.setBusinessDuration(request.getBusinessDuration());
		if (request.getMonthlyIncome() != null) app.setMonthlyIncome(request.getMonthlyIncome());
		if (request.getMonthlyExpenses() != null) app.setMonthlyExpenses(request.getMonthlyExpenses());
		if (request.getExistingLoans() != null) app.setExistingLoans(request.getExistingLoans());
		if (request.getDependentsCount() != null) app.setDependentsCount(request.getDependentsCount());
		if (request.getVehiclePrice() != null) app.setVehiclePrice(request.getVehiclePrice());
		if (request.getPrepaymentPercent() != null) app.setPrepaymentPercent(request.getPrepaymentPercent());
		if (request.getPrepaymentAmount() != null) app.setPrepaymentAmount(request.getPrepaymentAmount());
		if (request.getLoanAmount() != null) app.setLoanAmount(request.getLoanAmount());
		if (request.getLoanTermMonths() != null) app.setLoanTermMonths(request.getLoanTermMonths());
		if (request.getRepaymentMethod() != null) app.setRepaymentMethod(request.getRepaymentMethod());
		if (request.getBankCode() != null) app.setBankCode(request.getBankCode().trim().toUpperCase());
		if (request.getRequestPreDeposit() != null) app.setRequestPreDeposit(request.getRequestPreDeposit());
		if (request.getAgreedTerms() != null) app.setAgreedTerms(request.getAgreedTerms());
		if (request.getAgreedPrivacy() != null) app.setAgreedPrivacy(request.getAgreedPrivacy());
		if (request.getSignatureUrl() != null) {
			String sigUrl = request.getSignatureUrl();
			if (sigUrl.startsWith("data:image")) {
				sigUrl = cloudinaryDocumentService.uploadBase64Image(sigUrl, MediaUploadContext.INSTALLMENT_DOCUMENT, app.getId());
			}
			app.setSignatureUrl(sigUrl);
		}
		if (request.getSignedDate() != null) app.setSignedDate(request.getSignedDate());
		validateFinancialFields(app);
	}

	@Transactional
	public InstallmentDocumentResponse uploadDocument(Long customerId, Long id, String documentType, MultipartFile file) {
		InstallmentApplication app = applicationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (!app.getCustomer().getId().equals(customerId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Ban khong co quyen upload cho ho so nay.");
		}
		if (DEPOSIT_RECEIPT_DOC_TYPE.equalsIgnoreCase(documentType) && Boolean.TRUE.equals(app.getRequestPreDeposit())) {
			Deposit preDeposit = app.getPreDeposit();
			if (preDeposit == null || !isPaidDeposit(preDeposit)) {
				throw new BusinessException(
						ErrorCode.VALIDATION_FAILED,
						"Chi duoc upload chung tu coc sau khi da thanh toan coc thanh cong.");
			}
		}
		String url = cloudinaryDocumentService.uploadDocument(file, MediaUploadContext.INSTALLMENT_DOCUMENT, id);
		InstallmentDocument doc = new InstallmentDocument();
		doc.setApplication(app);
		doc.setDocumentType(documentType);
		doc.setDocumentUrl(url);
		doc.setOriginalFileName(file.getOriginalFilename());
		doc = documentRepository.save(doc);
		return InstallmentDocumentResponse.builder()
				.id(doc.getId())
				.documentType(doc.getDocumentType())
				.documentUrl(doc.getDocumentUrl())
				.originalFileName(doc.getOriginalFileName())
				.uploadedAt(doc.getUploadedAt())
				.build();
	}

	@Transactional
	public void deleteDocument(Long customerId, Long id, Long documentId) {
		InstallmentApplication app = applicationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (!app.getCustomer().getId().equals(customerId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Ban khong co quyen thao tac tren ho so nay.");
		}
		InstallmentDocument doc = documentRepository.findById(documentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay tai lieu."));
		if (!doc.getApplication().getId().equals(id)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tai lieu khong thuoc ho so nay.");
		}
		cloudinaryDocumentService.destroyDocumentByUrl(doc.getDocumentUrl());
		documentRepository.delete(doc);
	}

	public void appraiseApplication(Long staffId, String staffName, Long id) {
		InstallmentApplication app = applicationRepository.findByIdWithVehicleAndDocuments(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (app.getStatus() != InstallmentApplication.Status.PENDING_DOCUMENT
				&& app.getStatus() != InstallmentApplication.Status.DRAFT) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chi ho so DRAFT/PENDING_DOCUMENT moi duoc tham dinh.");
		}
		validateBankCodeRequired(app);
		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setStatus(InstallmentApplication.Status.BANK_PROCESSING);
		applicationRepository.save(app);
		recordStatusHistory(app, oldStatus, InstallmentApplication.Status.BANK_PROCESSING, "Gui tham dinh boi Staff #" + staffId);
		String bankLoanId;
		try {
			bankLoanId = bankIntegrationService.applyLoan(app, staffId, staffName);
		} catch (BankIntegrationService.CreditSyncException ex) {
			Integer httpCode = ex.getHttpCode();
			if (httpCode != null && httpCode >= 400 && httpCode < 500) {
				String upstream = ex.getUpstreamBody();
				String detail = (upstream == null || upstream.isBlank()) ? ex.getMessage() : upstream;
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Ho so gui tham dinh khong hop le: " + detail);
			}
			throw new BusinessException(ErrorCode.BANK_API_ERROR, ex.getMessage());
		}
		app.setBankLoanId(bankLoanId);
		applicationRepository.save(app);
	}

	public void handleBankWebhook(String rawPayload) throws Exception {
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		java.util.Map<String, Object> body = mapper.readValue(rawPayload, java.util.Map.class);
		String loanId = firstNonBlank(
				stringValue(body.get("loanId")),
				stringValue(body.get("loan_id")),
				stringValue(body.get("id")));
		String statusStr = firstNonBlank(
				stringValue(body.get("status")),
				stringValue(body.get("decision")),
				stringValue(body.get("result")));
		String reason = firstNonBlank(
				stringValue(body.get("rejectionReason")),
				stringValue(body.get("rejection_reason")),
				stringValue(body.get("reason")));
		String pdfUrl = firstNonBlank(
				stringValue(body.get("pdfUrl")),
				stringValue(body.get("pdf_url")),
				stringValue(body.get("contractUrl")));
		if (loanId == null || statusStr == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Payload thieu loanId hoac status.");
		}
		InstallmentApplication app = applicationRepository.findByBankLoanId(loanId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so cho loanId: " + loanId));
		applyCreditDecision(app, statusStr, reason, pdfUrl, "WEBHOOK");
	}

	private String stringValue(Object v) {
		if (v == null) return null;
		String s = v.toString().trim();
		return s.isEmpty() ? null : s;
	}

	private String firstNonBlank(String... values) {
		if (values == null) return null;
		for (String v : values) {
			if (v != null && !v.isBlank()) return v.trim();
		}
		return null;
	}

	@Transactional
	public void applyCreditDecision(
			InstallmentApplication app,
			String statusStr,
			String reason,
			String pdfUrl,
			String source) {
		InstallmentApplication.Status targetStatus;
		if ("APPROVED".equalsIgnoreCase(statusStr)) {
			targetStatus = InstallmentApplication.Status.APPROVED;
		} else if ("REJECTED".equalsIgnoreCase(statusStr)) {
			targetStatus = InstallmentApplication.Status.REJECTED;
		} else {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Status khong hop le: " + statusStr);
		}
		if (app.getStatus() == targetStatus) {
			log.info("Skip duplicate credit decision for appId={}, source={}, status={}", app.getId(), source, targetStatus);
			return;
		}

		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setStatus(targetStatus);
		if (targetStatus == InstallmentApplication.Status.APPROVED) {
			app.setBankPdfUrl(pdfUrl);
			app.setRejectionReason(null);
		} else {
			app.setRejectionReason(reason);
		}
		applicationRepository.save(app);

		InstallmentStatusHistory history = new InstallmentStatusHistory();
		history.setApplication(app);
		history.setOldStatus(oldStatus);
		history.setNewStatus(app.getStatus());
		history.setNote("Bank " + source + ": " + statusStr + (reason != null ? " - " + reason : ""));
		history.setChangedBy(null);
		statusHistoryRepository.save(history);
		sendWebhookNotification(app, statusStr, reason);
		notifyStaffAndManagersInstallmentDecision(app, statusStr, reason);
		saveWebhookAuditLog(app, statusStr, reason);
	}

	@Transactional
	public CreateDepositResponse createPreDeposit(Long customerId, String role, Long applicationId,
			CreateInstallmentPreDepositRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
		InstallmentApplication app = applicationRepository.findById(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (!app.getCustomer().getId().equals(customerId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Ban khong co quyen tao coc cho ho so nay.");
		}
		assertVehicleStillAvailableForCustomer(app.getVehicle(), customerId);
		if (!canCreatePreDepositFromStatus(app.getStatus())) {
			throw new BusinessException(
					ErrorCode.VALIDATION_FAILED,
					"Khong the tao coc truoc o trang thai " + app.getStatus().name() + ".");
		}
		// Frontend co the bat dau luong dat coc khi ho so chua danh dau requestPreDeposit.
		// Tu dong bat co nay de dong bo voi eligibility rule DEPOSIT_REQUIRED.
		if (!Boolean.TRUE.equals(app.getRequestPreDeposit())) {
			app.setRequestPreDeposit(true);
		}
		Deposit linkedPreDeposit = app.getPreDeposit();
		if (linkedPreDeposit != null) {
			String linkedStatus = linkedPreDeposit.getStatus() != null ? linkedPreDeposit.getStatus().trim() : "";
			if ("AwaitingPayment".equalsIgnoreCase(linkedStatus)) {
				assertInstallmentPreDepositNotTimedOut(linkedPreDeposit);
				return depositService.resumeOnlinePayment(
						customerId,
						role,
						linkedPreDeposit.getId(),
						HttpServletClientIp.resolve(httpRequest));
			}
			if (DEPOSIT_LOCK_STATUSES.stream().anyMatch(s -> s.equalsIgnoreCase(linkedStatus))) {
				return mapDepositToResponse(linkedPreDeposit, null);
			}
			// Lien ket coc cu khong con hieu luc (Cancelled/Failed/Expired...) -> cho phep tao lai.
			app.setPreDeposit(null);
			applicationRepository.save(app);
		}

		List<Deposit> activeDeposits = depositRepository.findByCustomerIdAndVehicleIdAndStatusIn(
				customerId,
				app.getVehicle().getId(),
				DEPOSIT_LOCK_STATUSES);
		if (!activeDeposits.isEmpty()) {
			Deposit existing = activeDeposits.stream()
					.max(Comparator.comparing(Deposit::getCreatedAt).thenComparing(Deposit::getId))
					.orElse(activeDeposits.get(0));
			app.setPreDeposit(existing);
			applicationRepository.save(app);
			if ("AwaitingPayment".equalsIgnoreCase(existing.getStatus())) {
				assertInstallmentPreDepositNotTimedOut(existing);
				installmentPaymentCacheService.saveSnapshot(mapToResponse(app));
				return depositService.resumeOnlinePayment(
						customerId,
						role,
						existing.getId(),
						HttpServletClientIp.resolve(httpRequest));
			}
			return mapDepositToResponse(existing, null);
		}
		BigDecimal vehiclePrice = resolveVehiclePriceForPreDeposit(app);
		if (vehiclePrice == null || vehiclePrice.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Vehicle price khong hop le de tinh coc truoc.");
		}
		BigDecimal percent = preDepositPercent == null ? new BigDecimal("10") : preDepositPercent;
		BigDecimal amount = vehiclePrice.multiply(percent).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
		if (amount.compareTo(BigDecimal.ONE) < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "So tien coc truoc khong hop le.");
		}
		CreateDepositRequest depositRequest = new CreateDepositRequest();
		depositRequest.setVehicleId(app.getVehicle().getId());
		depositRequest.setAmount(amount);
		depositRequest.setPaymentMethod(request.getPaymentMethod());
		depositRequest.setNote(request.getNote());
		CreateDepositResponse created = depositService.create(customerId, role, depositRequest, HttpServletClientIp.resolve(httpRequest));
		Deposit deposit = depositRepository.findById(created.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_NOT_FOUND, "Khong tim thay coc vua tao."));
		app.setPreDeposit(deposit);
		applicationRepository.save(app);
		installmentPaymentCacheService.saveSnapshot(mapToResponse(app));
		return created;
	}

	private BigDecimal resolveVehiclePriceForPreDeposit(InstallmentApplication app) {
		BigDecimal appPrice = app.getVehiclePrice();
		if (appPrice != null && appPrice.compareTo(BigDecimal.ZERO) > 0) {
			return appPrice;
		}
		Vehicle vehicle = app.getVehicle();
		if (vehicle == null) return null;
		BigDecimal vehiclePrice = vehicle.getPrice();
		if (vehiclePrice != null && vehiclePrice.compareTo(BigDecimal.ZERO) > 0) {
			// Backfill de cac lan sau khong phu thuoc payload frontend.
			app.setVehiclePrice(vehiclePrice);
			return vehiclePrice;
		}
		return null;
	}

	private void assertInstallmentPreDepositNotTimedOut(Deposit deposit) {
		if (deposit.getCreatedAt() == null) return;
		long minutesAgo = Duration.between(deposit.getCreatedAt(), Instant.now()).toMinutes();
		if (minutesAgo < INSTALLMENT_PRE_DEPOSIT_TIMEOUT_MINUTES) return;
		depositService.cancelPendingOnlineDepositTimedOut(deposit.getId());
		throw new BusinessException(
				ErrorCode.VALIDATION_FAILED,
				"Khoan coc truoc cho ho so tra gop da het han 1 gio. Vui long tao lai de tiep tuc.");
	}

	private CreateDepositResponse mapDepositToResponse(Deposit deposit, String paymentUrl) {
		return CreateDepositResponse.builder()
				.id(deposit.getId())
				.vehicleId(deposit.getVehicleId())
				.amount(deposit.getAmount() != null ? deposit.getAmount().toPlainString() : "0")
				.status(deposit.getStatus())
				.paymentUrl(paymentUrl)
				.depositDate(deposit.getDepositDate() != null ? deposit.getDepositDate().toString() : null)
				.expiryDate(deposit.getExpiryDate() != null ? deposit.getExpiryDate().toString() : null)
				.build();
	}

	private boolean canCreatePreDepositFromStatus(InstallmentApplication.Status status) {
		// Allow pre-deposit during active processing states; block terminal/invalid states.
		return status == InstallmentApplication.Status.DRAFT
				|| status == InstallmentApplication.Status.PENDING_DOCUMENT
				|| status == InstallmentApplication.Status.BANK_PROCESSING
				|| status == InstallmentApplication.Status.APPROVED
				|| status == InstallmentApplication.Status.DEPOSIT_PENDING;
	}

	private void sendWebhookNotification(InstallmentApplication app, String status, String reason) {
		try {
			InAppNotification notification = new InAppNotification();
			notification.setUser(app.getCustomer());
			notification.setType("INSTALLMENT");
			notification.setNotificationRead(false);
			notification.setLink("/installments/applications/" + app.getId());
			if ("APPROVED".equalsIgnoreCase(status)) {
				notification.setTitle("Ho so tra gop duoc phe duyet");
				notification.setBody("Ho so tra gop #" + app.getId() + " da duoc ngan hang phe duyet.");
			} else {
				notification.setTitle("Ho so tra gop bi tu choi");
				notification.setBody("Ho so tra gop #" + app.getId() + " bi tu choi."
						+ (reason != null ? " Ly do: " + reason : ""));
			}
			notificationRepository.save(notification);
		} catch (Exception e) {
			log.error("Loi gui notification cho customer {}: {}", app.getCustomer().getId(), e.getMessage());
		}
	}

	private void saveWebhookAuditLog(InstallmentApplication app, String status, String reason) {
		try {
			AuditLog auditLog = new AuditLog();
			auditLog.setUserId(null);
			auditLog.setUserName("SYSTEM_WEBHOOK");
			auditLog.setModule("INSTALLMENT");
			auditLog.setAction("WEBHOOK_" + status.toUpperCase());
			auditLog.setDetails("AppID: " + app.getId()
					+ " | LoanID: " + app.getBankLoanId()
					+ " | Status: " + status
					+ (reason != null ? " | Reason: " + reason : ""));
			auditLogRepository.save(auditLog);
		} catch (Exception e) {
			log.error("Loi ghi audit log webhook: {}", e.getMessage());
		}
	}

	@Transactional
	public void linkDeposit(Long staffId, Long applicationId, Long depositId) {
		InstallmentApplication app = applicationRepository.findById(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (app.getStatus() != InstallmentApplication.Status.APPROVED) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chi ho so APPROVED moi duoc lien ket coc.");
		}
		Deposit deposit = depositRepository.findById(depositId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay khoan coc."));
		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setDeposit(deposit);
		app.setStatus(InstallmentApplication.Status.DEPOSIT_PENDING);
		applicationRepository.save(app);
		recordStatusHistory(app, oldStatus, app.getStatus(), "Staff #" + staffId + " lien ket Deposit #" + depositId);
	}

	@Transactional
	public void handleDepositPaid(Long applicationId) {
		InstallmentApplication app = applicationRepository.findById(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (app.getStatus() != InstallmentApplication.Status.DEPOSIT_PENDING) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ho so khong o trang thai DEPOSIT_PENDING.");
		}
		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setStatus(InstallmentApplication.Status.DEPOSIT_PAID);
		applicationRepository.save(app);
		recordStatusHistory(app, oldStatus, app.getStatus(), "Coc thien chi da thanh toan.");
		try {
			InAppNotification noti = new InAppNotification();
			noti.setUser(app.getCustomer());
			noti.setType("INSTALLMENT");
			noti.setTitle("Coc thien chi da thanh toan");
			noti.setBody("Ho so tra gop #" + app.getId() + " da xac nhan coc thanh cong.");
			noti.setLink("/installments/applications/" + app.getId());
			noti.setNotificationRead(false);
			notificationRepository.save(noti);
		} catch (Exception e) {
			log.error("Loi gui notification deposit paid: {}", e.getMessage());
		}
	}

	@Transactional
	public void cancelApplication(Long userId, Long applicationId) {
		InstallmentApplication app = applicationRepository.findById(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));
		if (app.getStatus() == InstallmentApplication.Status.COMPLETED
				|| app.getStatus() == InstallmentApplication.Status.CANCELLED) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Khong the huy ho so o trang thai " + app.getStatus().name());
		}
		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setStatus(InstallmentApplication.Status.CANCELLED);
		applicationRepository.save(app);
		recordStatusHistory(app, oldStatus, app.getStatus(), "Huy boi User #" + userId);
	}

	@Transactional
	public int cleanupExpiredPreDepositPendingApplications() {
		Instant now = Instant.now();
		List<InstallmentApplication> apps = applicationRepository
				.findByStatusAndRequestPreDepositTrue(InstallmentApplication.Status.DEPOSIT_PENDING);
		int cleaned = 0;
		for (InstallmentApplication app : apps) {
			if (app.getCreatedAt() == null) continue;
			long minutes = Duration.between(app.getCreatedAt(), now).toMinutes();
			if (minutes < INSTALLMENT_PRE_DEPOSIT_TIMEOUT_MINUTES) continue;
			try {
				Deposit preDeposit = app.getPreDeposit();
				if (preDeposit != null && "AwaitingPayment".equalsIgnoreCase(preDeposit.getStatus())) {
					depositService.cancelPendingOnlineDepositTimedOut(preDeposit.getId());
				}
				InstallmentApplication.Status oldStatus = app.getStatus();
				app.setStatus(InstallmentApplication.Status.CANCELLED);
				applicationRepository.save(app);
				recordStatusHistory(app, oldStatus, app.getStatus(), "Auto cancel: qua han 1 gio cho coc truoc");
				cleaned++;
			} catch (Exception e) {
				log.warn("Khong the cleanup installment app #{} qua han coc truoc: {}", app.getId(), e.getMessage());
			}
		}
		return cleaned;
	}

	private void recordStatusHistory(InstallmentApplication app,
			InstallmentApplication.Status oldStatus,
			InstallmentApplication.Status newStatus,
			String note) {
		InstallmentStatusHistory h = new InstallmentStatusHistory();
		h.setApplication(app);
		h.setOldStatus(oldStatus);
		h.setNewStatus(newStatus);
		h.setNote(note);
		h.setChangedBy(null);
		statusHistoryRepository.save(h);
	}

	private InstallmentApplication.Status resolveRequestedStatus(
			Long customerId,
			Long vehicleId,
			InstallmentApplication app,
			InstallmentApplication.Status requested) {
		assertVehicleStillAvailableForCustomer(app.getVehicle(), customerId);
		if (requested != InstallmentApplication.Status.PENDING_DOCUMENT) {
			return requested;
		}
		InstallmentSubmitEligibilityResponse eligibility = buildSubmitEligibility(app, customerId, vehicleId);
		if (Boolean.TRUE.equals(eligibility.getCanSubmit())) {
			return InstallmentApplication.Status.PENDING_DOCUMENT;
		}
		// Neu chua co coc hop le theo xe/user thi bat co requestPreDeposit de frontend
		// vao dung luong dat coc -> thanh toan -> upload chung tu.
		if ("DEPOSIT_REQUIRED".equals(eligibility.getBlockingReason())) {
			app.setRequestPreDeposit(true);
		}
		throw new BusinessException(
				ErrorCode.VALIDATION_FAILED,
				"Chua du dieu kien gui ho so tra gop: " + eligibility.getBlockingReason());
	}

	private InstallmentApplicationResponse mapToResponse(InstallmentApplication app) {
		String vehicleTitle = "";
		try {
			Vehicle v = app.getVehicle();
			vehicleTitle = v.getTitle() != null ? v.getTitle() : "";
		} catch (Exception ignored) {
		}
		InstallmentSubmitEligibilityResponse eligibility = buildSubmitEligibility(
				app,
				app.getCustomer().getId(),
				app.getVehicle().getId());
		return InstallmentApplicationResponse.builder()
				.id(app.getId())
				.customerId(app.getCustomer().getId())
				.customerName(app.getCustomer().getName())
				.customerPhone(app.getCustomer().getPhone())
				.vehicleId(app.getVehicle().getId())
				.vehicleTitle(vehicleTitle)
				.depositId(app.getDeposit() != null ? app.getDeposit().getId() : null)
				.preDepositId(app.getPreDeposit() != null ? app.getPreDeposit().getId() : null)
				.bankLoanId(app.getBankLoanId())
				.fullName(app.getFullName())
				.identityNumber(app.getIdentityNumber())
				.phoneNumber(app.getPhoneNumber())
				.email(app.getEmail())
				.dob(app.getDob())
				.identityIssuedDate(app.getIdentityIssuedDate())
				.identityIssuedPlace(app.getIdentityIssuedPlace())
				.permanentAddress(app.getPermanentAddress())
				.currentAddress(app.getCurrentAddress())
				.employmentType(app.getEmploymentType())
				.companyName(app.getCompanyName())
				.jobTitle(app.getJobTitle())
				.workDuration(app.getWorkDuration())
				.salaryMethod(app.getSalaryMethod())
				.businessName(app.getBusinessName())
				.businessType(app.getBusinessType())
				.businessDuration(app.getBusinessDuration())
				.monthlyIncome(app.getMonthlyIncome())
				.monthlyExpenses(app.getMonthlyExpenses())
				.existingLoans(app.getExistingLoans())
				.dependentsCount(app.getDependentsCount())
				.vehiclePrice(app.getVehiclePrice())
				.prepaymentPercent(app.getPrepaymentPercent())
				.prepaymentAmount(app.getPrepaymentAmount())
				.loanAmount(app.getLoanAmount())
				.loanTermMonths(app.getLoanTermMonths())
				.repaymentMethod(app.getRepaymentMethod())
				.bankCode(app.getBankCode())
				.requestPreDeposit(app.getRequestPreDeposit())
				.agreedTerms(app.getAgreedTerms())
				.agreedPrivacy(app.getAgreedPrivacy())
				.signatureUrl(app.getSignatureUrl())
				.signedDate(app.getSignedDate())
				.status(app.getStatus().name())
				.rejectionReason(app.getRejectionReason())
				.bankPdfUrl(app.getBankPdfUrl())
				.hasValidDepositForVehicle(eligibility.getHasValidDepositForVehicle())
				.depositProofUploaded(eligibility.getDepositProofUploaded())
				.appliedDepositAmount(eligibility.getAppliedDepositAmount())
				.canSubmit(eligibility.getCanSubmit())
				.blockingReason(eligibility.getBlockingReason())
				.documents(app.getDocuments() != null ? app.getDocuments().stream().map(d -> InstallmentDocumentResponse.builder()
						.id(d.getId())
						.documentType(d.getDocumentType())
						.documentUrl(d.getDocumentUrl())
						.originalFileName(d.getOriginalFileName())
						.uploadedAt(d.getUploadedAt())
						.build()).collect(Collectors.toList()) : java.util.Collections.emptyList())
				.createdAt(app.getCreatedAt())
				.updatedAt(app.getUpdatedAt())
				.build();
	}

	private InstallmentSubmitEligibilityResponse buildSubmitEligibility(
			InstallmentApplication app,
			Long customerId,
			Long vehicleId) {
		List<Deposit> validDeposits = depositRepository.findByCustomerIdAndVehicleIdAndStatusIn(
				customerId, vehicleId, DEPOSIT_VALID_STATUSES);
		Deposit latestValid = validDeposits.stream()
				.max(Comparator.comparing(Deposit::getCreatedAt).thenComparing(Deposit::getId))
				.orElse(null);
		boolean hasValidDepositForVehicle = latestValid != null;
		BigDecimal appliedDepositAmount = latestValid != null && latestValid.getAmount() != null
				? latestValid.getAmount()
				: BigDecimal.ZERO;

		boolean depositProofUploaded = documentRepository.countByApplicationIdAndDocumentTypeIgnoreCase(
				app.getId(),
				DEPOSIT_RECEIPT_DOC_TYPE) > 0;

		boolean canSubmit;
		String blockingReason = null;
		if (!Boolean.TRUE.equals(app.getRequestPreDeposit())) {
			canSubmit = hasValidDepositForVehicle;
			if (!canSubmit) {
				blockingReason = "DEPOSIT_REQUIRED";
			}
		} else {
			Deposit preDeposit = app.getPreDeposit();
			if (preDeposit == null) {
				canSubmit = false;
				blockingReason = "PRE_DEPOSIT_REQUIRED";
			} else if (!isPaidDeposit(preDeposit)) {
				canSubmit = false;
				blockingReason = "PRE_DEPOSIT_PAYMENT_REQUIRED";
			} else if (!depositProofUploaded) {
				canSubmit = false;
				blockingReason = "DEPOSIT_PROOF_REQUIRED";
			} else {
				canSubmit = true;
			}
		}

		return InstallmentSubmitEligibilityResponse.builder()
				.applicationId(app.getId())
				.vehicleId(vehicleId)
				.hasValidDepositForVehicle(hasValidDepositForVehicle)
				.depositProofUploaded(depositProofUploaded)
				.appliedDepositAmount(appliedDepositAmount)
				.canSubmit(canSubmit)
				.blockingReason(blockingReason)
				.build();
	}

	private boolean isPaidDeposit(Deposit deposit) {
		if (deposit == null || deposit.getStatus() == null) return false;
		String status = deposit.getStatus().trim().toLowerCase();
		return "pending".equals(status) || "confirmed".equals(status);
	}

	private void assertVehicleStillAvailableForCustomer(Vehicle vehicle, Long customerId) {
		if (vehicle == null) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Khong tim thay xe.");
		}
		if (vehicle.isDeleted()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "VEHICLE_NOT_AVAILABLE: Xe khong con san sang cho ho so tra gop.");
		}
		long activeDepositsByOther = depositRepository.countByVehicleIdAndCustomerIdNotAndStatusIn(
				vehicle.getId(),
				customerId,
				DEPOSIT_LOCK_STATUSES);
		if (activeDepositsByOther > 0) {
			throw new BusinessException(
					ErrorCode.VALIDATION_FAILED,
					"VEHICLE_RESERVED_BY_OTHER: Xe da duoc nguoi khac dat coc/giu cho.");
		}
		long activeOrdersByOther = salesOrderRepository.countByVehicleIdAndCustomerIdNotAndStatusIn(
				vehicle.getId(),
				customerId,
				ORDER_LOCK_STATUSES);
		if (activeOrdersByOther > 0) {
			throw new BusinessException(
					ErrorCode.VALIDATION_FAILED,
					"VEHICLE_RESERVED_BY_OTHER: Xe da co don mua dang xu ly.");
		}
		// Neu xe dang o trang thai khong phai Available nhung KHONG bi nguoi khac giu cho,
		// van cho phep user hien tai tiep tuc ho so (vi co the xe dang duoc chinh user nay dat coc).
	}

	private void assertSingleActiveApplicationPerCustomer(Long customerId, Long currentApplicationId) {
		List<InstallmentApplication> apps = applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
		for (InstallmentApplication existing : apps) {
			if (currentApplicationId != null && existing.getId().equals(currentApplicationId)) continue;
			if (!SINGLE_CREATE_LOCK_STATUSES.contains(existing.getStatus())) continue;
			throw new BusinessException(
					ErrorCode.VALIDATION_FAILED,
					"SINGLE_ACTIVE_INSTALLMENT_ONLY: Ban chi duoc phep co 1 ho so tra gop dang tao tai mot thoi diem.");
		}
	}

	private void validateFinancialFields(InstallmentApplication app) {
		BigDecimal prepaymentPercent = app.getPrepaymentPercent();
		if (prepaymentPercent != null
				&& (prepaymentPercent.compareTo(MIN_PREPAY_PERCENT) < 0 || prepaymentPercent.compareTo(MAX_PREPAY_PERCENT) > 0)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Phan tram tra truoc phai trong khoang 30 den 70.");
		}
		if (prepaymentPercent == null || app.getVehiclePrice() == null || app.getPrepaymentAmount() == null) {
			return;
		}
		BigDecimal expected = app.getVehiclePrice().multiply(prepaymentPercent)
				.divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
		BigDecimal diff = expected.subtract(app.getPrepaymentAmount()).abs();
		if (diff.compareTo(PREPAY_AMOUNT_TOLERANCE) > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "So tien tra truoc khong khop voi phan tram da chon.");
		}
	}

	private void validateBankCodeRequired(InstallmentApplication app) {
		String code = app.getBankCode();
		if (code == null || code.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Bank code la bat buoc truoc khi tham dinh.");
		}
		String normalized = code.trim().toUpperCase();
		if (!SUPPORTED_BANK_CODES.contains(normalized)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Bank code khong hop le.");
		}
		app.setBankCode(normalized);
	}

	private void notifyStaffAndManagersNewInstallment(InstallmentApplication app) {
		try {
			List<User> recipients = userRepository.findActiveStaffAndManagersWithRoles();
			if (recipients == null || recipients.isEmpty()) return;
			for (User recipient : recipients) {
				InAppNotification noti = new InAppNotification();
				noti.setUser(recipient);
				noti.setType("INSTALLMENT");
				noti.setTitle("Có hồ sơ trả góp mới");
				noti.setBody("Ho so #" + app.getId() + " vua duoc gui, can duyet.");
				noti.setLink(resolveInstallmentInboxLinkForRecipient(recipient));
				noti.setNotificationRead(false);
				notificationRepository.save(noti);
			}
		} catch (Exception e) {
			log.error("Loi gui thong bao ho so tra gop moi #{}: {}", app.getId(), e.getMessage());
		}
	}

	private void notifyStaffAndManagersInstallmentDecision(InstallmentApplication app, String status, String reason) {
		try {
			List<User> recipients = userRepository.findActiveStaffAndManagersWithRoles();
			if (recipients == null || recipients.isEmpty()) return;
			boolean approved = "APPROVED".equalsIgnoreCase(status);
			for (User recipient : recipients) {
				InAppNotification noti = new InAppNotification();
				noti.setUser(recipient);
				noti.setType("INSTALLMENT");
				noti.setTitle(approved ? "Hồ sơ trả góp đã được phê duyệt" : "Hồ sơ trả góp bị từ chối");
				noti.setBody(
						approved
								? "Ho so #" + app.getId() + " da co ket qua phe duyet tu credit."
								: "Ho so #" + app.getId() + " bi tu choi."
										+ (reason != null && !reason.isBlank() ? " Ly do: " + reason : ""));
				noti.setLink(resolveInstallmentInboxLinkForRecipient(recipient));
				noti.setNotificationRead(false);
				notificationRepository.save(noti);
			}
		} catch (Exception e) {
			log.error("Loi gui thong bao cap nhat tham dinh cho ho so #{}: {}", app.getId(), e.getMessage());
		}
	}

	private String resolveInstallmentInboxLinkForRecipient(User recipient) {
		if (recipient == null || recipient.getUserRoles() == null) return "/staff/installments";
		boolean isManager = recipient.getUserRoles().stream()
				.map(UserRole::getRole)
				.filter(java.util.Objects::nonNull)
				.map(r -> r.getName())
				.filter(java.util.Objects::nonNull)
				.anyMatch(role -> "BranchManager".equalsIgnoreCase(role));
		return isManager ? "/manager/installments" : "/staff/installments";
	}
}
