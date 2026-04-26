package scu.dn.used_cars_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.installment.InstallmentApplicationResponse;
import scu.dn.used_cars_backend.dto.installment.SaveInstallmentApplicationRequest;
import scu.dn.used_cars_backend.entity.AuditLog;
import scu.dn.used_cars_backend.entity.InAppNotification;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.InstallmentStatusHistory;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.AuditLogRepository;
import scu.dn.used_cars_backend.repository.InAppNotificationRepository;
import scu.dn.used_cars_backend.repository.InstallmentApplicationRepository;
import scu.dn.used_cars_backend.repository.InstallmentDocumentRepository;
import scu.dn.used_cars_backend.repository.InstallmentStatusHistoryRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallmentServiceTest {

	@Mock private InstallmentApplicationRepository applicationRepository;
	@Mock private InstallmentDocumentRepository documentRepository;
	@Mock private UserRepository userRepository;
	@Mock private VehicleRepository vehicleRepository;
	@Mock private CloudinaryDocumentService cloudinaryDocumentService;
	@Mock private BankIntegrationService bankIntegrationService;
	@Mock private InstallmentStatusHistoryRepository statusHistoryRepository;
	@Mock private InAppNotificationRepository notificationRepository;
	@Mock private AuditLogRepository auditLogRepository;
	@Mock private scu.dn.used_cars_backend.repository.DepositRepository depositRepository;

	@InjectMocks
	private InstallmentService installmentService;

	private User mockCustomer;
	private Vehicle mockVehicle;
	private SaveInstallmentApplicationRequest mockRequest;
	private InstallmentApplication mockApp;

	@BeforeEach
	void setUp() {
		mockCustomer = new User();
		mockCustomer.setId(1L);

		mockVehicle = new Vehicle();
		mockVehicle.setId(100L);

		mockRequest = new SaveInstallmentApplicationRequest();
		mockRequest.setVehicleId(100L);
		mockRequest.setFullName("Nguyen Van A");
		mockRequest.setLoanAmount(new BigDecimal("100000000"));
		mockRequest.setStatus("DRAFT");

		mockApp = new InstallmentApplication();
		mockApp.setId(10L);
		mockApp.setCustomer(mockCustomer);
		mockApp.setVehicle(mockVehicle);
		mockApp.setStatus(InstallmentApplication.Status.DRAFT);
	}

	// ===== Phase 1-2: CRUD cơ bản =====

	@Nested
	@DisplayName("saveApplication")
	class SaveApplication {

		@Test
		@DisplayName("Tạo hồ sơ thành công — Happy Path")
		void success() {
			when(userRepository.findById(1L)).thenReturn(Optional.of(mockCustomer));
			when(vehicleRepository.findById(100L)).thenReturn(Optional.of(mockVehicle));
			when(applicationRepository.save(any(InstallmentApplication.class))).thenAnswer(inv -> {
				InstallmentApplication saved = inv.getArgument(0);
				saved.setId(10L);
				return saved;
			});

			InstallmentApplicationResponse res = installmentService.saveApplication(1L, mockRequest);

			assertNotNull(res);
			assertEquals(10L, res.getId());
			assertEquals("Nguyen Van A", res.getFullName());
			assertEquals("DRAFT", res.getStatus());
		}

		@Test
		@DisplayName("Lỗi khi User không tồn tại")
		void userNotFound() {
			when(userRepository.findById(1L)).thenReturn(Optional.empty());

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.saveApplication(1L, mockRequest));
			assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
		}
	}

	@Nested
	@DisplayName("updateApplication")
	class UpdateApplication {

		@Test
		@DisplayName("Forbidden khi Customer sửa hồ sơ người khác")
		void forbidden() {
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.updateApplication(2L, 10L, mockRequest));
			assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
		}
	}

	// ===== Phase 3: Bank Integration =====

	@Nested
	@DisplayName("appraiseApplication")
	class AppraiseApplication {

		@Test
		@DisplayName("Gửi thẩm định thành công — status đổi sang BANK_PROCESSING")
		void success() {
			mockApp.setStatus(InstallmentApplication.Status.DRAFT);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));
			when(bankIntegrationService.applyLoan(any(), eq(5L), eq("Staff A"))).thenReturn("LOAN-001");
			when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			installmentService.appraiseApplication(5L, "Staff A", 10L);

			assertEquals(InstallmentApplication.Status.BANK_PROCESSING, mockApp.getStatus());
			assertEquals("LOAN-001", mockApp.getBankLoanId());
		}

		@Test
		@DisplayName("Lỗi khi hồ sơ có trạng thái không hợp lệ")
		void invalidStatus() {
			mockApp.setStatus(InstallmentApplication.Status.APPROVED);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.appraiseApplication(5L, "Staff A", 10L));
			assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
		}
	}

	// ===== Phase 4: Webhook Handler =====

	@Nested
	@DisplayName("handleBankWebhook")
	class HandleBankWebhook {

		private ObjectMapper objectMapper = new ObjectMapper();

		@Test
		@DisplayName("APPROVED — cập nhật status, ghi StatusHistory, gửi Notification, ghi AuditLog")
		void approved() throws Exception {
			mockApp.setStatus(InstallmentApplication.Status.BANK_PROCESSING);
			mockApp.setBankLoanId("LOAN-001");
			when(applicationRepository.findByBankLoanId("LOAN-001")).thenReturn(Optional.of(mockApp));
			when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			String payload = objectMapper.writeValueAsString(Map.of(
					"loanId", "LOAN-001",
					"status", "APPROVED",
					"pdfUrl", "https://bank.com/result.pdf"
			));

			installmentService.handleBankWebhook(payload);

			// Assert: status đã đổi
			assertEquals(InstallmentApplication.Status.APPROVED, mockApp.getStatus());
			assertEquals("https://bank.com/result.pdf", mockApp.getBankPdfUrl());

			// Assert: ghi InstallmentStatusHistory
			ArgumentCaptor<InstallmentStatusHistory> histCaptor = ArgumentCaptor.forClass(InstallmentStatusHistory.class);
			verify(statusHistoryRepository).save(histCaptor.capture());
			InstallmentStatusHistory hist = histCaptor.getValue();
			assertEquals(InstallmentApplication.Status.BANK_PROCESSING, hist.getOldStatus());
			assertEquals(InstallmentApplication.Status.APPROVED, hist.getNewStatus());

			// Assert: gửi InAppNotification cho Customer
			ArgumentCaptor<InAppNotification> notiCaptor = ArgumentCaptor.forClass(InAppNotification.class);
			verify(notificationRepository).save(notiCaptor.capture());
			InAppNotification noti = notiCaptor.getValue();
			assertEquals("INSTALLMENT", noti.getType());
			assertTrue(noti.getTitle().contains("phê duyệt"));
			assertEquals(mockCustomer, noti.getUser());

			// Assert: ghi AuditLog
			ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
			verify(auditLogRepository).save(auditCaptor.capture());
			AuditLog audit = auditCaptor.getValue();
			assertEquals("INSTALLMENT", audit.getModule());
			assertEquals("WEBHOOK_APPROVED", audit.getAction());
		}

		@Test
		@DisplayName("REJECTED — lưu lý do, gửi Notification với nội dung từ chối")
		void rejected() throws Exception {
			mockApp.setStatus(InstallmentApplication.Status.BANK_PROCESSING);
			mockApp.setBankLoanId("LOAN-002");
			when(applicationRepository.findByBankLoanId("LOAN-002")).thenReturn(Optional.of(mockApp));
			when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			String payload = objectMapper.writeValueAsString(Map.of(
					"loanId", "LOAN-002",
					"status", "REJECTED",
					"rejectionReason", "Thu nhap khong du"
			));

			installmentService.handleBankWebhook(payload);

			// Assert: status REJECTED và rejectionReason được lưu
			assertEquals(InstallmentApplication.Status.REJECTED, mockApp.getStatus());
			assertEquals("Thu nhap khong du", mockApp.getRejectionReason());

			// Assert: Notification gửi cho Customer với lý do
			ArgumentCaptor<InAppNotification> notiCaptor = ArgumentCaptor.forClass(InAppNotification.class);
			verify(notificationRepository).save(notiCaptor.capture());
			InAppNotification noti = notiCaptor.getValue();
			assertTrue(noti.getTitle().contains("từ chối"));
			assertTrue(noti.getBody().contains("Thu nhap khong du"));

			// Assert: AuditLog ghi action WEBHOOK_REJECTED
			ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
			verify(auditLogRepository).save(auditCaptor.capture());
			assertEquals("WEBHOOK_REJECTED", auditCaptor.getValue().getAction());
		}

		@Test
		@DisplayName("Payload thiếu loanId — lỗi VALIDATION_FAILED")
		void missingLoanId() {
			String payload = "{\"status\":\"APPROVED\"}";

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.handleBankWebhook(payload));
			assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
		}

		@Test
		@DisplayName("loanId không tồn tại — lỗi RESOURCE_NOT_FOUND")
		void loanNotFound() throws Exception {
			when(applicationRepository.findByBankLoanId("INVALID-LOAN")).thenReturn(Optional.empty());

			String payload = objectMapper.writeValueAsString(Map.of(
					"loanId", "INVALID-LOAN",
					"status", "APPROVED"
			));

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.handleBankWebhook(payload));
			assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		}
	}

	// ===== Phase 5: Payment Gateway =====

	@Nested
	@DisplayName("linkDeposit")
	class LinkDeposit {

		@Test
		@DisplayName("Link deposit thành công — APPROVED → DEPOSIT_PENDING")
		void success() {
			mockApp.setStatus(InstallmentApplication.Status.APPROVED);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));

			scu.dn.used_cars_backend.entity.Deposit mockDeposit = new scu.dn.used_cars_backend.entity.Deposit();
			mockDeposit.setId(50L);
			when(depositRepository.findById(50L)).thenReturn(Optional.of(mockDeposit));
			when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			installmentService.linkDeposit(5L, 10L, 50L);

			assertEquals(InstallmentApplication.Status.DEPOSIT_PENDING, mockApp.getStatus());
			assertEquals(mockDeposit, mockApp.getDeposit());
			verify(statusHistoryRepository).save(any(InstallmentStatusHistory.class));
		}

		@Test
		@DisplayName("Lỗi khi hồ sơ chưa APPROVED")
		void invalidStatus() {
			mockApp.setStatus(InstallmentApplication.Status.BANK_PROCESSING);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.linkDeposit(5L, 10L, 50L));
			assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
		}
	}

	@Nested
	@DisplayName("handleDepositPaid")
	class HandleDepositPaid {

		@Test
		@DisplayName("Thanh toán cọc thành công — DEPOSIT_PENDING → DEPOSIT_PAID + gửi Notification")
		void success() {
			mockApp.setStatus(InstallmentApplication.Status.DEPOSIT_PENDING);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));
			when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			installmentService.handleDepositPaid(10L);

			assertEquals(InstallmentApplication.Status.DEPOSIT_PAID, mockApp.getStatus());
			verify(statusHistoryRepository).save(any(InstallmentStatusHistory.class));
			verify(notificationRepository).save(any(InAppNotification.class));
		}

		@Test
		@DisplayName("Lỗi khi hồ sơ không ở DEPOSIT_PENDING")
		void invalidStatus() {
			mockApp.setStatus(InstallmentApplication.Status.APPROVED);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.handleDepositPaid(10L));
			assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
		}
	}

	@Nested
	@DisplayName("cancelApplication")
	class CancelApplication {

		@Test
		@DisplayName("Hủy hồ sơ thành công — DRAFT → CANCELLED")
		void success() {
			mockApp.setStatus(InstallmentApplication.Status.DRAFT);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));
			when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			installmentService.cancelApplication(1L, 10L);

			assertEquals(InstallmentApplication.Status.CANCELLED, mockApp.getStatus());
			verify(statusHistoryRepository).save(any(InstallmentStatusHistory.class));
		}

		@Test
		@DisplayName("Lỗi khi hồ sơ đã COMPLETED")
		void alreadyCompleted() {
			mockApp.setStatus(InstallmentApplication.Status.COMPLETED);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.cancelApplication(1L, 10L));
			assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
		}

		@Test
		@DisplayName("Lỗi khi hồ sơ đã CANCELLED")
		void alreadyCancelled() {
			mockApp.setStatus(InstallmentApplication.Status.CANCELLED);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.cancelApplication(1L, 10L));
			assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
		}
	}

	// ===== Phase 10: Bổ sung Test Coverage =====

	@Nested
	@DisplayName("completeApplication")
	class CompleteApplication {

		@Test
		@DisplayName("Hoàn tất thành công từ APPROVED")
		void fromApproved() {
			mockApp.setStatus(InstallmentApplication.Status.APPROVED);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));
			when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			installmentService.completeApplication(5L, 10L);

			assertEquals(InstallmentApplication.Status.COMPLETED, mockApp.getStatus());
			verify(statusHistoryRepository).save(any(InstallmentStatusHistory.class));
			verify(notificationRepository).save(any(InAppNotification.class));
		}

		@Test
		@DisplayName("Hoàn tất thành công từ DEPOSIT_PAID")
		void fromDepositPaid() {
			mockApp.setStatus(InstallmentApplication.Status.DEPOSIT_PAID);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));
			when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			installmentService.completeApplication(5L, 10L);

			assertEquals(InstallmentApplication.Status.COMPLETED, mockApp.getStatus());
		}

		@Test
		@DisplayName("Lỗi khi hồ sơ DRAFT — không cho complete")
		void draftCannotComplete() {
			mockApp.setStatus(InstallmentApplication.Status.DRAFT);
			when(applicationRepository.findById(10L)).thenReturn(Optional.of(mockApp));

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.completeApplication(5L, 10L));
			assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
		}

		@Test
		@DisplayName("Lỗi khi hồ sơ không tồn tại")
		void notFound() {
			when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.completeApplication(5L, 999L));
			assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		}
	}

	@Nested
	@DisplayName("handleBankWebhook — edge cases bổ sung")
	class HandleBankWebhookExtra {

		private ObjectMapper om = new ObjectMapper();

		@Test
		@DisplayName("Status không hợp lệ (UNKNOWN) → lỗi VALIDATION_FAILED")
		void unknownStatus() throws Exception {
			mockApp.setStatus(InstallmentApplication.Status.BANK_PROCESSING);
			mockApp.setBankLoanId("LOAN-X");
			when(applicationRepository.findByBankLoanId("LOAN-X")).thenReturn(Optional.of(mockApp));

			String payload = om.writeValueAsString(Map.of(
					"loanId", "LOAN-X",
					"status", "UNKNOWN_STATUS"
			));

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.handleBankWebhook(payload));
			assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
		}

		@Test
		@DisplayName("Payload thiếu status → lỗi VALIDATION_FAILED")
		void missingStatus() {
			String payload = "{\"loanId\":\"LOAN-X\"}";

			BusinessException ex = assertThrows(BusinessException.class,
					() -> installmentService.handleBankWebhook(payload));
			assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
		}
	}
}
