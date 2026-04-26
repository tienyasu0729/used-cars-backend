package scu.dn.used_cars_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import scu.dn.used_cars_backend.config.FakeBankProperties;
import scu.dn.used_cars_backend.repository.AuditLogRepository;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BankIntegrationServiceTest {

	@Mock private AuditLogRepository auditLogRepository;

	private static final String TEST_SECRET = "my-secret-key";

	private BankIntegrationService createService() {
		FakeBankProperties props = new FakeBankProperties(
				"https://fake.bank/api/loan/apply", "test-api-key", TEST_SECRET);
		return new BankIntegrationService(
				props, new com.fasterxml.jackson.databind.ObjectMapper(), auditLogRepository);
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
	@DisplayName("verifyWebhookSignature — chữ ký hợp lệ trả true")
	void verifyValidSignature() throws Exception {
		BankIntegrationService service = createService();
		String payload = "{\"loanId\":\"L001\",\"status\":\"APPROVED\"}";
		String sig = computeHmac(payload, TEST_SECRET);
		assertTrue(service.verifyWebhookSignature(payload, sig));
	}

	@Test
	@DisplayName("verifyWebhookSignature — chữ ký sai trả false")
	void verifyInvalidSignature() {
		BankIntegrationService service = createService();
		String payload = "{\"loanId\":\"L001\"}";
		assertFalse(service.verifyWebhookSignature(payload, "invalid-signature-abc123"));
	}

	@Test
	@DisplayName("HMAC consistency — cùng payload + secret cho cùng kết quả")
	void hmacConsistency() throws Exception {
		BankIntegrationService service = createService();
		String payload = "{\"test\":\"data\",\"value\":123}";
		String sig1 = computeHmac(payload, TEST_SECRET);
		String sig2 = computeHmac(payload, TEST_SECRET);
		assertEquals(sig1, sig2);
		assertTrue(service.verifyWebhookSignature(payload, sig1));
	}

	@Test
	@DisplayName("HMAC — payload khác nhau cho chữ ký khác nhau")
	void hmacDifferentPayloads() throws Exception {
		String sig1 = computeHmac("{\"a\":1}", TEST_SECRET);
		String sig2 = computeHmac("{\"a\":2}", TEST_SECRET);
		assertNotEquals(sig1, sig2);
	}

	@Test
	@DisplayName("HMAC — secret khác nhau cho chữ ký khác nhau")
	void hmacDifferentSecrets() throws Exception {
		String payload = "{\"loanId\":\"L001\"}";
		String sig1 = computeHmac(payload, TEST_SECRET);
		String sig2 = computeHmac(payload, "different-secret");
		assertNotEquals(sig1, sig2);

		BankIntegrationService service = createService();
		assertTrue(service.verifyWebhookSignature(payload, sig1));
		assertFalse(service.verifyWebhookSignature(payload, sig2));
	}

	@Test
	@DisplayName("HMAC — payload rỗng vẫn tạo được chữ ký hợp lệ")
	void hmacEmptyPayload() throws Exception {
		BankIntegrationService service = createService();
		String payload = "";
		String sig = computeHmac(payload, TEST_SECRET);
		assertNotNull(sig);
		assertFalse(sig.isEmpty());
		assertTrue(service.verifyWebhookSignature(payload, sig));
	}

	@Test
	@DisplayName("HMAC — payload UTF-8 (tiếng Việt) verify đúng")
	void hmacUtf8Payload() throws Exception {
		BankIntegrationService service = createService();
		String payload = "{\"fullName\":\"Nguyễn Văn A\",\"status\":\"APPROVED\"}";
		String sig = computeHmac(payload, TEST_SECRET);
		assertTrue(service.verifyWebhookSignature(payload, sig));
	}
}
