package scu.dn.used_cars_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.config.FakeBankProperties;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.InstallmentDocument;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.AuditLogRepository;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BankIntegrationServiceTest {

	@Mock
	private AuditLogRepository auditLogRepository;

	private static final String TEST_SECRET = "my-secret-key";

	private BankIntegrationService createService() {
		FakeBankProperties props = new FakeBankProperties(
				"https://fake.bank/api/loan/apply",
				"https://fake.bank/api/loan/status",
				"test-api-key",
				TEST_SECRET,
				10000,
				3000,
				new FakeBankProperties.Retry(5000L, 900000L, 20));
		BankIntegrationService service = new BankIntegrationService(
				props, new com.fasterxml.jackson.databind.ObjectMapper(), auditLogRepository);
		ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:5173");
		return service;
	}

	private String computeHmac(String payload, String secret) throws Exception {
		javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
		mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		StringBuilder hex = new StringBuilder();
		for (byte b : hash) {
			String h = Integer.toHexString(0xff & b);
			if (h.length() == 1) hex.append('0');
			hex.append(h);
		}
		return hex.toString();
	}

	@Test
	@DisplayName("verifyWebhookSignature valid signature")
	void verifyValidSignature() throws Exception {
		BankIntegrationService service = createService();
		String payload = "{\"loanId\":\"L001\",\"status\":\"APPROVED\"}";
		String sig = computeHmac(payload, TEST_SECRET);
		assertTrue(service.verifyWebhookSignature(payload, sig));
	}

	@Test
	@DisplayName("verifyWebhookSignature valid signature with timestamp mode")
	void verifyValidSignatureWithTimestampMode() throws Exception {
		BankIntegrationService service = createService();
		String payload = "{\"loanId\":\"L001\",\"status\":\"APPROVED\"}";
		String timestamp = "1714639600";
		String sig = computeHmac(payload + "." + timestamp, TEST_SECRET);
		assertTrue(service.verifyWebhookSignature(payload, sig, timestamp));
	}

	@Test
	@DisplayName("verifyWebhookSignature invalid signature")
	void verifyInvalidSignature() {
		BankIntegrationService service = createService();
		String payload = "{\"loanId\":\"L001\"}";
		assertFalse(service.verifyWebhookSignature(payload, "invalid-signature-abc123"));
	}

	@Test
	@DisplayName("submitLoan missing required field fail-fast")
	void submitLoan_missingRequiredPayloadField_failFast() {
		BankIntegrationService service = createService();
		InstallmentApplication app = buildRichApplication();
		app.setFullName(null);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.submitLoan(app, 1L, "staff", "idem-1"));
		assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
	}

	@Test
	@DisplayName("buildLoanPayload maps full contract snapshot")
	@SuppressWarnings("unchecked")
	void buildLoanPayload_containsFullContract() throws Exception {
		BankIntegrationService service = createService();
		InstallmentApplication app = buildRichApplication();

		Method m = BankIntegrationService.class.getDeclaredMethod("buildLoanPayload", InstallmentApplication.class);
		m.setAccessible(true);
		Map<String, Object> payload = (Map<String, Object>) m.invoke(service, app);

		assertEquals("Dang Van Nam", payload.get("customerName"));
		assertEquals("qa_customer@test.com", payload.get("customerEmail"));
		assertEquals("0900000100", payload.get("customerPhone"));
		assertEquals("0900000100", payload.get("phone"));
		assertEquals("Toyota Vios", payload.get("vehicleModel"));
		assertEquals(new BigDecimal("500000000"), payload.get("vehiclePrice"));
		assertEquals(300000000L, payload.get("loanAmount"));
		assertEquals(300000000L, payload.get("amount"));
		assertEquals("VCB", payload.get("bankCode"));
		assertEquals(36L, payload.get("loanTermMonths"));
		assertEquals(36L, payload.get("term"));
		assertEquals("040099148316", payload.get("identityNumber"));
		assertEquals("040099148316", payload.get("cccd"));
		assertEquals("Dang Van Nam", payload.get("fullName"));
		assertEquals("0900000100", payload.get("phoneNumber"));
		assertEquals("qa_customer@test.com", payload.get("email"));

		Map<String, Object> documents = (Map<String, Object>) payload.get("documents");
		assertEquals("https://example.com/cccd.jpg", documents.get("cccdUrl"));
		List<Map<String, Object>> allDocuments = (List<Map<String, Object>>) documents.get("allDocuments");
		assertEquals(2, allDocuments.size());
		Map<String, Object> byType = (Map<String, Object>) documents.get("byType");
		assertTrue(byType.containsKey("CCCD_FRONT"));
		assertTrue(byType.containsKey("INCOME_PROOF"));
		List<String> cccdUrls = (List<String>) byType.get("CCCD_FRONT");
		assertEquals("https://example.com/cccd.jpg", cccdUrls.get(0));

		Map<String, Object> step1 = (Map<String, Object>) payload.get("step1Personal");
		assertEquals("Dang Van Nam", step1.get("fullName"));
		assertEquals("040099148316", step1.get("cccd"));
		assertEquals("Cuc CS QLHC ve TTXH", step1.get("identityIssuedPlace"));

		Map<String, Object> step2 = (Map<String, Object>) payload.get("step2Occupation");
		assertEquals("SALARIED", step2.get("employmentType"));
		assertEquals("ABC Co", step2.get("companyName"));

		Map<String, Object> step3 = (Map<String, Object>) payload.get("step3Finance");
		assertEquals(new BigDecimal("30000000"), step3.get("monthlyIncome"));
		assertEquals(2, step3.get("dependentsCount"));

		Map<String, Object> step4 = (Map<String, Object>) payload.get("step4Loan");
		assertEquals(36L, step4.get("term"));
		assertEquals("VCB", step4.get("bankCode"));

		Map<String, Object> step5 = (Map<String, Object>) payload.get("step5Documents");
		assertEquals("https://example.com/cccd.jpg", step5.get("cccdUrl"));

		Map<String, Object> step6 = (Map<String, Object>) payload.get("step6Commitment");
		assertEquals(true, step6.get("agreedTerms"));
		assertEquals("https://example.com/signature.png", step6.get("signatureUrl"));

		Map<String, Object> step7 = (Map<String, Object>) payload.get("step7Confirmation");
		assertEquals("BANK_PROCESSING", step7.get("status"));
		assertEquals("LOAN-001", step7.get("bankLoanId"));
		Map<String, Object> step7Vehicle = (Map<String, Object>) step7.get("vehicleSnapshot");
		assertEquals("Toyota Vios", step7Vehicle.get("title"));

		Map<String, Object> vehicleSnapshot = (Map<String, Object>) payload.get("vehicleSnapshot");
		assertEquals(101L, vehicleSnapshot.get("id"));
		assertEquals("Toyota Vios", vehicleSnapshot.get("title"));
		assertEquals("http://localhost:5173/vehicles/101", vehicleSnapshot.get("detailUrl"));
		Map<String, Object> vehicleAlias = (Map<String, Object>) payload.get("vehicle");
		assertEquals("Toyota Vios", vehicleAlias.get("title"));

		Map<String, Object> applicationSnapshot = (Map<String, Object>) payload.get("applicationSnapshot");
		assertEquals("BANK_PROCESSING", applicationSnapshot.get("status"));
		assertNotNull(applicationSnapshot.get("documents"));
		assertNotNull(applicationSnapshot.get("step1Personal"));
		assertNotNull(applicationSnapshot.get("step2Occupation"));
		assertNotNull(applicationSnapshot.get("step3Finance"));
		assertNotNull(applicationSnapshot.get("step4Loan"));
		assertNotNull(applicationSnapshot.get("step5Documents"));
		assertNotNull(applicationSnapshot.get("step6Commitment"));
		assertNotNull(applicationSnapshot.get("step7Confirmation"));
		Map<String, Object> appVehicleSnapshot = (Map<String, Object>) applicationSnapshot.get("vehicleSnapshot");
		assertEquals("Toyota Vios", appVehicleSnapshot.get("title"));
		assertEquals("http://localhost:5173/vehicles/101", appVehicleSnapshot.get("detailUrl"));
		Map<String, Object> appVehicleAlias = (Map<String, Object>) applicationSnapshot.get("vehicle");
		assertEquals("Toyota Vios", appVehicleAlias.get("title"));

		assertEquals(Instant.parse("2026-05-02T05:00:00Z"), payload.get("createdAt"));
		assertEquals(Instant.parse("2026-05-02T05:30:00Z"), payload.get("updatedAt"));
	}

	@Test
	@DisplayName("buildLoanPayload missing cccd document fail-fast")
	void buildLoanPayload_missingCccdDocument_failFast() {
		BankIntegrationService service = createService();
		InstallmentApplication app = buildRichApplication();
		app.setDocuments(List.of());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.submitLoan(app, 1L, "staff", "idem-no-doc"));
		assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
		assertTrue(ex.getMessage().contains("CCCD"));
	}

	@Test
	@DisplayName("buildLoanPayload filters invalid URL from documents.byType")
	@SuppressWarnings("unchecked")
	void buildLoanPayload_filtersInvalidUrlInByType() throws Exception {
		BankIntegrationService service = createService();
		InstallmentApplication app = buildRichApplication();
		InstallmentDocument invalid = new InstallmentDocument();
		invalid.setDocumentType("INCOME_PROOF");
		invalid.setDocumentUrl("file:///tmp/local.png");
		invalid.setOriginalFileName("local.png");
		invalid.setUploadedAt(Instant.parse("2026-05-02T06:40:00Z"));
		app.setDocuments(List.of(app.getDocuments().get(0), invalid));

		Method m = BankIntegrationService.class.getDeclaredMethod("buildLoanPayload", InstallmentApplication.class);
		m.setAccessible(true);
		Map<String, Object> payload = (Map<String, Object>) m.invoke(service, app);
		Map<String, Object> documents = (Map<String, Object>) payload.get("documents");
		Map<String, Object> byType = (Map<String, Object>) documents.get("byType");
		List<String> incomeUrls = (List<String>) byType.get("INCOME_PROOF");
		assertTrue(incomeUrls == null || incomeUrls.isEmpty());
	}

	private InstallmentApplication buildRichApplication() {
		InstallmentApplication app = new InstallmentApplication();
		Vehicle vehicle = new Vehicle();
		vehicle.setId(101L);
		vehicle.setListingId("LST-101");
		vehicle.setTitle("Toyota Vios");
		vehicle.setPrice(new BigDecimal("500000000"));
		vehicle.setYear(2020);
		vehicle.setFuel("Xang");
		vehicle.setTransmission("AT");
		vehicle.setMileage(45000);
		vehicle.setBodyStyle("Sedan");
		vehicle.setOrigin("VN");
		vehicle.setStatus("Available");
		vehicle.setCreatedAt(Instant.parse("2026-05-01T01:00:00Z"));
		vehicle.setUpdatedAt(Instant.parse("2026-05-02T01:00:00Z"));

		app.setVehicle(vehicle);
		app.setVehiclePrice(new BigDecimal("500000000"));
		app.setLoanAmount(new BigDecimal("300000000"));
		app.setLoanTermMonths(36);
		app.setBankCode("VCB");
		app.setIdentityNumber("040099148316");
		app.setPhoneNumber("0900000100");
		app.setEmail("qa_customer@test.com");
		app.setFullName("Dang Van Nam");
		app.setDob(LocalDate.parse("1997-10-10"));
		app.setIdentityIssuedDate(LocalDate.parse("2020-02-20"));
		app.setIdentityIssuedPlace("Cuc CS QLHC ve TTXH");
		app.setPermanentAddress("12 Nguyen Van Linh, Da Nang");
		app.setCurrentAddress("12 Nguyen Van Linh, Da Nang");
		app.setEmploymentType("SALARIED");
		app.setCompanyName("ABC Co");
		app.setJobTitle("QA");
		app.setWorkDuration("5 years");
		app.setSalaryMethod("BANK_TRANSFER");
		app.setBusinessName("NA");
		app.setBusinessType("NA");
		app.setBusinessDuration("0");
		app.setMonthlyIncome(new BigDecimal("30000000"));
		app.setMonthlyExpenses(new BigDecimal("12000000"));
		app.setExistingLoans(new BigDecimal("1000000"));
		app.setDependentsCount(2);
		app.setPrepaymentPercent(new BigDecimal("40"));
		app.setPrepaymentAmount(new BigDecimal("200000000"));
		app.setRepaymentMethod("EQUAL_INSTALLMENT");
		app.setRequestPreDeposit(true);
		app.setAgreedTerms(true);
		app.setAgreedPrivacy(true);
		app.setSignatureUrl("https://example.com/signature.png");
		app.setSignedDate(LocalDate.parse("2026-05-02"));
		app.setStatus(InstallmentApplication.Status.BANK_PROCESSING);
		app.setBankLoanId("LOAN-001");
		app.setCreatedAt(Instant.parse("2026-05-02T05:00:00Z"));
		app.setUpdatedAt(Instant.parse("2026-05-02T05:30:00Z"));

		InstallmentDocument idDoc = new InstallmentDocument();
		idDoc.setDocumentType("CCCD_FRONT");
		idDoc.setDocumentUrl("https://example.com/cccd.jpg");
		idDoc.setOriginalFileName("cccd-front.jpg");
		idDoc.setUploadedAt(Instant.parse("2026-05-02T06:30:00Z"));
		InstallmentDocument incomeDoc = new InstallmentDocument();
		incomeDoc.setDocumentType("INCOME_PROOF");
		incomeDoc.setDocumentUrl("https://example.com/income.pdf");
		incomeDoc.setOriginalFileName("income.pdf");
		incomeDoc.setUploadedAt(Instant.parse("2026-05-02T06:31:00Z"));
		app.setDocuments(List.of(idDoc, incomeDoc));
		return app;
	}
}
