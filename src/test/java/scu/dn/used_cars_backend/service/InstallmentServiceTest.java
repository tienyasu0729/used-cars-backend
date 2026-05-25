package scu.dn.used_cars_backend.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.installment.CreateInstallmentPreDepositRequest;
import scu.dn.used_cars_backend.dto.installment.SaveInstallmentApplicationRequest;
import scu.dn.used_cars_backend.dto.sales.CreateDepositResponse;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.User;
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
import scu.dn.used_cars_backend.sms.entity.OtpVerification;
import scu.dn.used_cars_backend.sms.repository.OtpVerificationRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstallmentServiceTest {
	@Mock private InstallmentApplicationRepository applicationRepository;
	@Mock private InstallmentDocumentRepository documentRepository;
	@Mock private UserRepository userRepository;
	@Mock private VehicleRepository vehicleRepository;
	@Mock private CloudinaryDocumentService cloudinaryDocumentService;
	@Mock private InstallmentStatusHistoryRepository statusHistoryRepository;
	@Mock private InAppNotificationRepository notificationRepository;
	@Mock private AuditLogRepository auditLogRepository;
	@Mock private DepositRepository depositRepository;
	@Mock private DepositService depositService;
	@Mock private SalesOrderRepository salesOrderRepository;
	@Mock private InstallmentPaymentCacheService installmentPaymentCacheService;
	@Mock private OtpVerificationRepository otpVerificationRepository;
	@Mock private HttpServletRequest httpServletRequest;

	@InjectMocks
	private InstallmentService installmentService;

	private SaveInstallmentApplicationRequest req;
	private InstallmentApplication app;
	private User customer;
	private Vehicle vehicle;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(installmentService, "preDepositPercent", new BigDecimal("10"));

		customer = new User();
		customer.setId(1L);
		customer.setName("User A");
		customer.setPhone("0900000001");
		customer.setProfileCompletionRequired(false);
		vehicle = new Vehicle();
		vehicle.setId(100L);
		vehicle.setTitle("Car");

		req = new SaveInstallmentApplicationRequest();
		req.setVehicleId(100L);
		req.setFullName("User A");
		req.setVehiclePrice(new BigDecimal("500000000"));
		req.setPrepaymentPercent(new BigDecimal("30"));
		req.setPrepaymentAmount(new BigDecimal("150000000"));
		req.setBankCode("VCB");
		req.setStatus("DRAFT");

		app = new InstallmentApplication();
		app.setId(10L);
		app.setCustomer(customer);
		app.setVehicle(vehicle);
		app.setStatus(InstallmentApplication.Status.DRAFT);
		app.setVehiclePrice(new BigDecimal("500000000"));
		app.setPrepaymentPercent(new BigDecimal("30"));
		app.setPrepaymentAmount(new BigDecimal("150000000"));
		app.setBankCode("VCB");
		app.setLoanTermMonths(36);
		app.setLoanAmount(new BigDecimal("350000000"));
		app.setFullName("User A");
		app.setPhoneNumber("0900000001");
		app.setEmail("usera@test.com");
		app.setIdentityNumber("040099148316");

		lenient().when(depositRepository.countByVehicleIdAndCustomerIdNotAndStatusIn(anyLong(), anyLong(), any()))
				.thenReturn(0L);
		lenient().when(salesOrderRepository.countByVehicleIdAndCustomerIdNotAndStatusIn(anyLong(), anyLong(), any()))
				.thenReturn(0L);
	}

	@Test
	void saveApplication_ok() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
		when(vehicleRepository.findById(100L)).thenReturn(Optional.of(vehicle));
		when(applicationRepository.save(any(InstallmentApplication.class))).thenAnswer(inv -> inv.getArgument(0));

		var res = installmentService.saveApplication(1L, req);
		assertEquals("VCB", res.getBankCode());
		assertEquals(new BigDecimal("30"), res.getPrepaymentPercent());
	}

	@Test
	void saveApplication_percentBelow30_fail() {
		req.setPrepaymentPercent(new BigDecimal("29.99"));
		when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
		when(vehicleRepository.findById(100L)).thenReturn(Optional.of(vehicle));

		BusinessException ex = assertThrows(BusinessException.class, () -> installmentService.saveApplication(1L, req));
		assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
	}

	@Test
	void saveApplication_percentAbove70_fail() {
		req.setPrepaymentPercent(new BigDecimal("70.01"));
		when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
		when(vehicleRepository.findById(100L)).thenReturn(Optional.of(vehicle));

		BusinessException ex = assertThrows(BusinessException.class, () -> installmentService.saveApplication(1L, req));
		assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
	}

	@Test
	void markBankProcessing_ok() {
		app.setStatus(InstallmentApplication.Status.PENDING_DOCUMENT);
		when(applicationRepository.findByIdWithVehicleAndDocuments(10L)).thenReturn(Optional.of(app));
		when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		installmentService.markBankProcessing(5L, "staff", 10L);
		assertEquals(InstallmentApplication.Status.BANK_PROCESSING, app.getStatus());
	}

	@Test
	void approveApplication_ok() {
		app.setStatus(InstallmentApplication.Status.BANK_PROCESSING);
		when(applicationRepository.findByIdWithVehicleAndDocuments(10L)).thenReturn(Optional.of(app));
		when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		installmentService.approveApplication(5L, "staff", 10L);
		assertEquals(InstallmentApplication.Status.APPROVED, app.getStatus());
	}

	@Test
	void createPreDeposit_ok() {
		app.setRequestPreDeposit(true);
		when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));
		when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(depositService.create(eq(1L), eq("CUSTOMER"), any(), any())).thenReturn(
				CreateDepositResponse.builder().id(50L).vehicleId(100L).amount("50000000").status("AwaitingPayment").build());
		Deposit d = new Deposit();
		d.setId(50L);
		when(depositRepository.findById(50L)).thenReturn(Optional.of(d));
		when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");

		CreateInstallmentPreDepositRequest request = new CreateInstallmentPreDepositRequest();
		request.setPaymentMethod("vnpay");
		var res = installmentService.createPreDeposit(1L, "CUSTOMER", 10L, request, httpServletRequest);

		assertEquals(50L, res.getId());
		assertEquals(50L, app.getPreDeposit().getId());
	}

	@Test
	void createPreDeposit_secondTime_returnsLinkedDeposit() {
		app.setRequestPreDeposit(true);
		Deposit existing = new Deposit();
		existing.setId(80L);
		existing.setStatus("Pending");
		existing.setVehicleId(100L);
		app.setPreDeposit(existing);
		when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));

		CreateInstallmentPreDepositRequest request = new CreateInstallmentPreDepositRequest();
		request.setPaymentMethod("vnpay");
		var res = installmentService.createPreDeposit(1L, "CUSTOMER", 10L, request, httpServletRequest);
		assertEquals(80L, res.getId());
	}

	@Test
	void createPreDeposit_whenRequestPreDepositFalse_autoEnableAndCreate() {
		app.setRequestPreDeposit(false);
		app.setPreDeposit(null);
		when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));
		when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(depositService.create(eq(1L), eq("CUSTOMER"), any(), any())).thenReturn(
				CreateDepositResponse.builder().id(51L).vehicleId(100L).amount("50000000").status("AwaitingPayment").build());
		Deposit d = new Deposit();
		d.setId(51L);
		when(depositRepository.findById(51L)).thenReturn(Optional.of(d));
		when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");

		CreateInstallmentPreDepositRequest request = new CreateInstallmentPreDepositRequest();
		request.setPaymentMethod("vnpay");
		var res = installmentService.createPreDeposit(1L, "CUSTOMER", 10L, request, httpServletRequest);

		assertEquals(51L, res.getId());
		assertTrue(Boolean.TRUE.equals(app.getRequestPreDeposit()));
		assertEquals(51L, app.getPreDeposit().getId());
	}

	@Test
	void createPreDeposit_whenApplicationVehiclePriceMissing_fallbackToVehiclePrice() {
		app.setRequestPreDeposit(true);
		app.setVehiclePrice(null);
		vehicle.setPrice(new BigDecimal("620000000"));
		app.setVehicle(vehicle);
		when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));
		when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(depositService.create(eq(1L), eq("CUSTOMER"), any(), any())).thenReturn(
				CreateDepositResponse.builder().id(52L).vehicleId(100L).amount("62000000").status("AwaitingPayment").build());
		Deposit d = new Deposit();
		d.setId(52L);
		when(depositRepository.findById(52L)).thenReturn(Optional.of(d));
		when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");

		CreateInstallmentPreDepositRequest request = new CreateInstallmentPreDepositRequest();
		request.setPaymentMethod("vnpay");
		var res = installmentService.createPreDeposit(1L, "CUSTOMER", 10L, request, httpServletRequest);

		assertEquals(52L, res.getId());
		assertEquals(new BigDecimal("620000000"), app.getVehiclePrice());
	}

	@Test
	void updateApplication_pendingDocumentWithoutOtp_fail() {
		req.setStatus("PENDING_DOCUMENT");
		app.setStatus(InstallmentApplication.Status.DRAFT);
		app.setRequestPreDeposit(false);
		when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));
		when(depositRepository.findByCustomerIdAndVehicleIdAndStatusIn(anyLong(), anyLong(), any()))
				.thenReturn(java.util.List.of(new Deposit()));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> installmentService.updateApplication(1L, 10L, req));
		assertEquals(ErrorCode.OTP_REFERENCE_INVALID, ex.getErrorCode());
	}

	@Test
	void updateApplication_pendingDocumentWithVerifiedOtp_ok() {
		req.setStatus("PENDING_DOCUMENT");
		req.setOtpVerificationId(999L);
		req.setPhoneNumber("0900000001");
		app.setStatus(InstallmentApplication.Status.DRAFT);
		app.setRequestPreDeposit(false);
		when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));
		when(depositRepository.findByCustomerIdAndVehicleIdAndStatusIn(anyLong(), anyLong(), any()))
				.thenReturn(java.util.List.of(new Deposit()));
		when(applicationRepository.existsByOtpVerificationId(999L)).thenReturn(false);

		OtpVerification otp = new OtpVerification();
		otp.setId(999L);
		otp.setPhone("0900000001");
		otp.setReferenceType("installment");
		otp.setReferenceId(10L);
		otp.setStatus(OtpVerification.STATUS_VERIFIED);
		otp.setVerifiedAt(Instant.now());
		when(otpVerificationRepository.findById(999L)).thenReturn(Optional.of(otp));
		when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var res = installmentService.updateApplication(1L, 10L, req);
		assertEquals("PENDING_DOCUMENT", res.getStatus());
		assertEquals(999L, app.getOtpVerificationId());
	}

}
