package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.installment.InstallmentApplicationResponse;
import scu.dn.used_cars_backend.dto.installment.SaveInstallmentApplicationRequest;
import scu.dn.used_cars_backend.entity.AuditLog;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.InAppNotification;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.InstallmentStatusHistory;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.AuditLogRepository;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.InAppNotificationRepository;
import scu.dn.used_cars_backend.repository.InstallmentApplicationRepository;
import scu.dn.used_cars_backend.repository.InstallmentDocumentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.entity.InstallmentDocument;
import scu.dn.used_cars_backend.dto.installment.InstallmentDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstallmentService {

	private final InstallmentApplicationRepository applicationRepository;
	private final InstallmentDocumentRepository documentRepository;
	private final UserRepository userRepository;
	private final VehicleRepository vehicleRepository;
	private final CloudinaryDocumentService cloudinaryDocumentService;
	private final BankIntegrationService bankIntegrationService;
	private final scu.dn.used_cars_backend.repository.InstallmentStatusHistoryRepository statusHistoryRepository;
	private final InAppNotificationRepository notificationRepository;
	private final AuditLogRepository auditLogRepository;
	private final DepositRepository depositRepository;

	@Transactional
	public InstallmentApplicationResponse saveApplication(Long customerId, SaveInstallmentApplicationRequest request) {
		User customer = userRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));

		Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));

		var existing = applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
				.stream()
				.filter(a -> a.getVehicle().getId().equals(request.getVehicleId())
						&& a.getStatus() == InstallmentApplication.Status.DRAFT)
				.findFirst();

		if (existing.isPresent()) {
			InstallmentApplication app = existing.get();
			updateApplicationFields(app, request);
			app = applicationRepository.save(app);
			return mapToResponse(app);
		}

		InstallmentApplication app = new InstallmentApplication();
		app.setCustomer(customer);
		app.setVehicle(vehicle);

		updateApplicationFields(app, request);

		if (request.getStatus() != null) {
			try {
				app.setStatus(InstallmentApplication.Status.valueOf(request.getStatus().toUpperCase()));
			} catch (IllegalArgumentException e) {
				app.setStatus(InstallmentApplication.Status.DRAFT);
			}
		} else {
			app.setStatus(InstallmentApplication.Status.DRAFT);
		}

		app = applicationRepository.save(app);
		return mapToResponse(app);
	}

	@Transactional
	public InstallmentApplicationResponse updateApplication(Long customerId, Long id, SaveInstallmentApplicationRequest request) {
		InstallmentApplication app = applicationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ."));

		if (!app.getCustomer().getId().equals(customerId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền sửa hồ sơ này.");
		}

		updateApplicationFields(app, request);

		if (request.getStatus() != null) {
			try {
				InstallmentApplication.Status newStatus = InstallmentApplication.Status.valueOf(request.getStatus().toUpperCase());
				app.setStatus(newStatus);
			} catch (IllegalArgumentException e) {
				// keep current
			}
		}

		app = applicationRepository.save(app);
		return mapToResponse(app);
	}

	@Transactional(readOnly = true)
	public InstallmentApplicationResponse getApplication(Long userId, String role, Long id) {
		InstallmentApplication app = applicationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ."));

		if (!"ADMIN".equals(role) && !"SALESSTAFF".equalsIgnoreCase(role) && !app.getCustomer().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem hồ sơ này.");
		}

		return mapToResponse(app);
	}

	@Transactional(readOnly = true)
	public List<InstallmentApplicationResponse> getMyApplications(Long customerId) {
		return applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
				.stream().map(this::mapToResponse).collect(Collectors.toList());
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
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ."));

		if (app.getStatus() != InstallmentApplication.Status.APPROVED
				&& app.getStatus() != InstallmentApplication.Status.DEPOSIT_PAID) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Chỉ hồ sơ APPROVED hoặc DEPOSIT_PAID mới được hoàn tất.");
		}

		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setStatus(InstallmentApplication.Status.COMPLETED);
		applicationRepository.save(app);

		recordStatusHistory(app, oldStatus, app.getStatus(), "Hoàn tất bởi Staff #" + staffId);

		try {
			InAppNotification noti = new InAppNotification();
			noti.setUser(app.getCustomer());
			noti.setType("INSTALLMENT");
			noti.setTitle("Hồ sơ trả góp hoàn tất");
			noti.setBody("Hồ sơ trả góp #" + app.getId() + " đã hoàn tất. Cảm ơn bạn đã tin tưởng!");
			noti.setLink("/installments/applications/" + app.getId());
			noti.setNotificationRead(false);
			notificationRepository.save(noti);
		} catch (Exception e) {
			log.error("Lỗi gửi notification complete: {}", e.getMessage());
		}
	}

	@Transactional
	public InstallmentApplicationResponse saveApplicationOnBehalf(Long staffId, Long customerId, SaveInstallmentApplicationRequest request) {
		User customer = userRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy khách hàng."));

		Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));

		var existing = applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
				.stream()
				.filter(a -> a.getVehicle().getId().equals(request.getVehicleId())
						&& a.getStatus() == InstallmentApplication.Status.DRAFT)
				.findFirst();

		if (existing.isPresent()) {
			InstallmentApplication app = existing.get();
			updateApplicationFields(app, request);
			app = applicationRepository.save(app);
			return mapToResponse(app);
		}

		InstallmentApplication app = new InstallmentApplication();
		app.setCustomer(customer);
		app.setVehicle(vehicle);
		updateApplicationFields(app, request);

		if (request.getStatus() != null) {
			try {
				app.setStatus(InstallmentApplication.Status.valueOf(request.getStatus().toUpperCase()));
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
			auditLog.setDetails("Staff #" + staffId + " tạo hồ sơ cho KH #" + customerId + " | AppID: " + app.getId());
			auditLogRepository.save(auditLog);
		} catch (Exception e) {
			log.error("Lỗi ghi audit log on-behalf: {}", e.getMessage());
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
		if (request.getPrepaymentAmount() != null) app.setPrepaymentAmount(request.getPrepaymentAmount());
		if (request.getLoanAmount() != null) app.setLoanAmount(request.getLoanAmount());
		if (request.getLoanTermMonths() != null) app.setLoanTermMonths(request.getLoanTermMonths());
		if (request.getRepaymentMethod() != null) app.setRepaymentMethod(request.getRepaymentMethod());

		if (request.getAgreedTerms() != null) app.setAgreedTerms(request.getAgreedTerms());
		if (request.getAgreedPrivacy() != null) app.setAgreedPrivacy(request.getAgreedPrivacy());
		if (request.getSignatureUrl() != null) {
			String sigUrl = request.getSignatureUrl();
			if (sigUrl.startsWith("data:image")) {
				sigUrl = cloudinaryDocumentService.uploadBase64Image(
						sigUrl, MediaUploadContext.INSTALLMENT_DOCUMENT, app.getId());
			}
			app.setSignatureUrl(sigUrl);
		}
		if (request.getSignedDate() != null) app.setSignedDate(request.getSignedDate());
	}

	@Transactional
	public InstallmentDocumentResponse uploadDocument(Long customerId, Long id, String documentType, MultipartFile file) {
		InstallmentApplication app = applicationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ."));
		if (!app.getCustomer().getId().equals(customerId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền upload cho hồ sơ này.");
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
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ."));
		if (!app.getCustomer().getId().equals(customerId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thao tác trên hồ sơ này.");
		}
		
		InstallmentDocument doc = documentRepository.findById(documentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài liệu."));
				
		if (!doc.getApplication().getId().equals(id)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tài liệu không thuộc hồ sơ này.");
		}
		
		cloudinaryDocumentService.destroyDocumentByUrl(doc.getDocumentUrl());
		documentRepository.delete(doc);
	}

	@Transactional
	public void appraiseApplication(Long staffId, String staffName, Long id) {
		InstallmentApplication app = applicationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ."));
		
		if (app.getStatus() != InstallmentApplication.Status.PENDING_DOCUMENT && app.getStatus() != InstallmentApplication.Status.DRAFT) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chỉ hồ sơ DRAFT hoặc PENDING_DOCUMENT mới được gửi định giá.");
		}

		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setStatus(InstallmentApplication.Status.BANK_PROCESSING);
		applicationRepository.save(app);

		try {
			String loanId = bankIntegrationService.applyLoan(app, staffId, staffName);
			app.setBankLoanId(loanId);
			applicationRepository.save(app);
			recordStatusHistory(app, oldStatus, InstallmentApplication.Status.BANK_PROCESSING, "Gửi thẩm định bởi Staff #" + staffId);
		} catch (BusinessException e) {
			app.setStatus(oldStatus);
			applicationRepository.save(app);
			throw e;
		}
	}

	@Transactional
	public void handleBankWebhook(String rawPayload) throws Exception {
		// B1: Parse payload
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		java.util.Map<String, Object> body = mapper.readValue(rawPayload, java.util.Map.class);
		
		String loanId = (String) body.get("loanId");
		String statusStr = (String) body.get("status");
		String reason = (String) body.get("rejectionReason");
		String pdfUrl = (String) body.get("pdfUrl");
		
		if (loanId == null || statusStr == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Payload thiếu loanId hoặc status.");
		}
		
		// B2: Lấy hồ sơ
		InstallmentApplication app = applicationRepository.findByBankLoanId(loanId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ cho loanId: " + loanId));
				
		InstallmentApplication.Status oldStatus = app.getStatus();
		
		// B3: Cập nhật trạng thái
		if ("APPROVED".equalsIgnoreCase(statusStr)) {
			app.setStatus(InstallmentApplication.Status.APPROVED);
			app.setBankPdfUrl(pdfUrl);
		} else if ("REJECTED".equalsIgnoreCase(statusStr)) {
			app.setStatus(InstallmentApplication.Status.REJECTED);
			app.setRejectionReason(reason);
		} else {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Status không hợp lệ: " + statusStr);
		}
		
		applicationRepository.save(app);
		
		// B4: Ghi log lịch sử trạng thái
		InstallmentStatusHistory history = new InstallmentStatusHistory();
		history.setApplication(app);
		history.setOldStatus(oldStatus);
		history.setNewStatus(app.getStatus());
		history.setNote("Bank Webhook: " + statusStr + (reason != null ? " - " + reason : ""));
		history.setChangedBy(null); // Hệ thống tự động
		statusHistoryRepository.save(history);
		
		// B5: Gửi Notification cho Customer
		sendWebhookNotification(app, statusStr, reason);
		
		// B6: Ghi AuditLog
		saveWebhookAuditLog(app, statusStr, reason);
	}

	// Gửi thông báo trong ứng dụng cho khách hàng khi Bank trả kết quả
	private void sendWebhookNotification(InstallmentApplication app, String status, String reason) {
		try {
			InAppNotification notification = new InAppNotification();
			notification.setUser(app.getCustomer());
			notification.setType("INSTALLMENT");
			notification.setNotificationRead(false);
			notification.setLink("/installments/applications/" + app.getId());

			if ("APPROVED".equalsIgnoreCase(status)) {
				notification.setTitle("Hồ sơ trả góp được phê duyệt");
				notification.setBody("Hồ sơ trả góp #" + app.getId() + " cho xe đã được ngân hàng phê duyệt. Vui lòng kiểm tra chi tiết.");
			} else {
				notification.setTitle("Hồ sơ trả góp bị từ chối");
				notification.setBody("Hồ sơ trả góp #" + app.getId() + " đã bị từ chối."
						+ (reason != null ? " Lý do: " + reason : " Vui lòng liên hệ nhân viên để biết thêm chi tiết."));
			}

			notificationRepository.save(notification);
		} catch (Exception e) {
			log.error("Lỗi khi gửi notification cho customer {}: {}", app.getCustomer().getId(), e.getMessage());
		}
	}

	// Ghi log audit cho mỗi webhook nhận từ Bank
	private void saveWebhookAuditLog(InstallmentApplication app, String status, String reason) {
		try {
			AuditLog auditLog = new AuditLog();
			auditLog.setUserId(null); // Hệ thống tự động (không phải user cụ thể)
			auditLog.setUserName("SYSTEM_WEBHOOK");
			auditLog.setModule("INSTALLMENT");
			auditLog.setAction("WEBHOOK_" + status.toUpperCase());
			auditLog.setDetails("AppID: " + app.getId()
					+ " | LoanID: " + app.getBankLoanId()
					+ " | Status: " + status
					+ (reason != null ? " | Reason: " + reason : ""));
			auditLogRepository.save(auditLog);
		} catch (Exception e) {
			log.error("Lỗi khi ghi audit log cho webhook: {}", e.getMessage());
		}
	}

	// ===== Phase 5: Payment Gateway — Liên kết Deposit =====

	// Staff liên kết Deposit (cọc thiện chí) với hồ sơ trả góp đã APPROVED
	@Transactional
	public void linkDeposit(Long staffId, Long applicationId, Long depositId) {
		InstallmentApplication app = applicationRepository.findById(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ."));

		if (app.getStatus() != InstallmentApplication.Status.APPROVED) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Chỉ hồ sơ APPROVED mới được liên kết cọc thiện chí.");
		}

		Deposit deposit = depositRepository.findById(depositId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khoản cọc."));

		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setDeposit(deposit);
		app.setStatus(InstallmentApplication.Status.DEPOSIT_PENDING);
		applicationRepository.save(app);

		// Ghi lịch sử
		recordStatusHistory(app, oldStatus, app.getStatus(), "Staff #" + staffId + " liên kết Deposit #" + depositId);
	}

	// Đánh dấu cọc đã thanh toán thành công cho hồ sơ trả góp
	@Transactional
	public void handleDepositPaid(Long applicationId) {
		InstallmentApplication app = applicationRepository.findById(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ."));

		if (app.getStatus() != InstallmentApplication.Status.DEPOSIT_PENDING) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Hồ sơ không ở trạng thái chờ cọc (DEPOSIT_PENDING).");
		}

		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setStatus(InstallmentApplication.Status.DEPOSIT_PAID);
		applicationRepository.save(app);

		// Ghi lịch sử
		recordStatusHistory(app, oldStatus, app.getStatus(), "Cọc thiện chí đã thanh toán thành công.");

		// Thông báo cho Customer
		try {
			InAppNotification noti = new InAppNotification();
			noti.setUser(app.getCustomer());
			noti.setType("INSTALLMENT");
			noti.setTitle("Cọc thiện chí đã thanh toán");
			noti.setBody("Hồ sơ trả góp #" + app.getId() + " đã xác nhận cọc thiện chí thành công.");
			noti.setLink("/installments/applications/" + app.getId());
			noti.setNotificationRead(false);
			notificationRepository.save(noti);
		} catch (Exception e) {
			log.error("Lỗi gửi notification deposit paid: {}", e.getMessage());
		}
	}

	// Hủy hồ sơ trả góp
	@Transactional
	public void cancelApplication(Long userId, Long applicationId) {
		InstallmentApplication app = applicationRepository.findById(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ."));

		// Chỉ cho phép hủy khi chưa COMPLETED hoặc đã CANCELLED
		if (app.getStatus() == InstallmentApplication.Status.COMPLETED ||
			app.getStatus() == InstallmentApplication.Status.CANCELLED) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Không thể hủy hồ sơ ở trạng thái " + app.getStatus().name());
		}

		InstallmentApplication.Status oldStatus = app.getStatus();
		app.setStatus(InstallmentApplication.Status.CANCELLED);
		applicationRepository.save(app);

		recordStatusHistory(app, oldStatus, app.getStatus(), "Hủy bởi User #" + userId);
	}

	// Phương thức helper ghi lịch sử trạng thái
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

	private InstallmentApplicationResponse mapToResponse(InstallmentApplication app) {
		String vehicleTitle = "";
		try {
			Vehicle v = app.getVehicle();
			vehicleTitle = v.getTitle() != null ? v.getTitle() : "";
		} catch (Exception e) {
			// lazy load
		}

		return InstallmentApplicationResponse.builder()
				.id(app.getId())
				.customerId(app.getCustomer().getId())
				.customerName(app.getCustomer().getName())
				.customerPhone(app.getCustomer().getPhone())
				.vehicleId(app.getVehicle().getId())
				.vehicleTitle(vehicleTitle)
				.depositId(app.getDeposit() != null ? app.getDeposit().getId() : null)
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
				.prepaymentAmount(app.getPrepaymentAmount())
				.loanAmount(app.getLoanAmount())
				.loanTermMonths(app.getLoanTermMonths())
				.repaymentMethod(app.getRepaymentMethod())
				.agreedTerms(app.getAgreedTerms())
				.agreedPrivacy(app.getAgreedPrivacy())
				.signatureUrl(app.getSignatureUrl())
				.signedDate(app.getSignedDate())
				.status(app.getStatus().name())
				.rejectionReason(app.getRejectionReason())
				.bankPdfUrl(app.getBankPdfUrl())
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
}
