package scu.dn.used_cars_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.config.FakeBankProperties;
import scu.dn.used_cars_backend.entity.AuditLog;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.InstallmentDocument;
import scu.dn.used_cars_backend.repository.AuditLogRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Service xử lý logic gọi API sang dịch vụ thẩm định tín dụng
// Bao gồm: tính toán chữ ký HMAC SHA256, gửi HTTP POST hồ sơ trả góp, và ghi log Audit
@Service
@RequiredArgsConstructor
@Slf4j
public class BankIntegrationService {

	private final FakeBankProperties properties;
	private final ObjectMapper objectMapper;
	private final AuditLogRepository auditLogRepository;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public String applyLoan(InstallmentApplication app, Long staffId, String staffName) {
		try {
			// B1: Xây dựng payload JSON
			Map<String, Object> payload = new HashMap<>();
			payload.put("fullName", app.getFullName());
			payload.put("identityNumber", app.getIdentityNumber());
			payload.put("phoneNumber", app.getPhoneNumber());
			payload.put("email", app.getEmail());
			payload.put("dob", app.getDob() != null ? app.getDob().toString() : null);
			payload.put("identityIssuedDate", app.getIdentityIssuedDate() != null ? app.getIdentityIssuedDate().toString() : null);
			payload.put("identityIssuedPlace", app.getIdentityIssuedPlace());
			payload.put("permanentAddress", app.getPermanentAddress());
			payload.put("currentAddress", app.getCurrentAddress());

			payload.put("employmentType", app.getEmploymentType());
			payload.put("companyName", app.getCompanyName());
			payload.put("jobTitle", app.getJobTitle());
			payload.put("workDuration", app.getWorkDuration());
			payload.put("salaryMethod", app.getSalaryMethod());
			payload.put("businessName", app.getBusinessName());
			payload.put("businessType", app.getBusinessType());
			payload.put("businessDuration", app.getBusinessDuration());
			payload.put("monthlyIncome", app.getMonthlyIncome());
			payload.put("monthlyExpenses", app.getMonthlyExpenses());
			payload.put("existingLoans", app.getExistingLoans());
			payload.put("dependentsCount", app.getDependentsCount());

			payload.put("vehiclePrice", app.getVehiclePrice());
			payload.put("prepaymentAmount", app.getPrepaymentAmount());
			payload.put("loanAmount", app.getLoanAmount());
			payload.put("loanTermMonths", app.getLoanTermMonths());
			payload.put("repaymentMethod", app.getRepaymentMethod());

			payload.put("signatureUrl", app.getSignatureUrl());
			payload.put("signedDate", app.getSignedDate() != null ? app.getSignedDate().toString() : null);

			Map<String, List<String>> documentsByType = app.getDocuments().stream()
					.collect(Collectors.groupingBy(
							InstallmentDocument::getDocumentType,
							Collectors.mapping(InstallmentDocument::getDocumentUrl, Collectors.toList())
					));
			payload.put("documentUrls", documentsByType);

			String jsonPayload = objectMapper.writeValueAsString(payload);
			long timestamp = Instant.now().getEpochSecond();
			
			// B2: Tạo chữ ký HMAC SHA256
			String signature = generateHmacSha256(jsonPayload, properties.secret());

			// B3: Gọi API Bank
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(properties.url()))
					.header("Content-Type", "application/json")
					.header("X-API-Key", properties.apiKey())
					.header("X-Timestamp", String.valueOf(timestamp))
					.header("X-Signature", signature)
					.POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			// B4: Xử lý kết quả & ghi log
			boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
			
			saveAuditLog(staffId, staffName, app.getId(), success, response.body());

			if (success) {
				@SuppressWarnings("unchecked")
				Map<String, Object> resBody = objectMapper.readValue(response.body(), Map.class);
				return (String) resBody.get("loanId");
			} else {
				log.error("Bank API error: {}", response.body());
				throw new BusinessException(ErrorCode.BANK_API_ERROR, "Lỗi từ hệ thống ngân hàng: HTTP " + response.statusCode());
			}

		} catch (BusinessException be) {
			throw be;
		} catch (Exception e) {
			log.error("Bank integration failed", e);
			saveAuditLog(staffId, staffName, app.getId(), false, e.getMessage());
			throw new BusinessException(ErrorCode.BANK_CONNECTION_ERROR, "Không thể kết nối tới ngân hàng. Vui lòng thử lại sau.");
		}
	}

	public boolean verifyWebhookSignature(String payload, String signature) {
		try {
			String expectedSignature = generateHmacSha256(payload, properties.secret());
			return expectedSignature.equals(signature);
		} catch (Exception e) {
			log.error("Error verifying webhook signature", e);
			return false;
		}
	}

	private String generateHmacSha256(String data, String key) throws Exception {
		Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
		SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		sha256_HMAC.init(secret_key);
		byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
		StringBuilder hexString = new StringBuilder();
		for (byte b : hash) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}
	
	private void saveAuditLog(Long userId, String userName, Long appId, boolean success, String details) {
		try {
			AuditLog logEntry = new AuditLog();
			logEntry.setUserId(userId);
			logEntry.setUserName(userName);
			logEntry.setModule("INSTALLMENT");
			logEntry.setAction(success ? "BANK_APPLY_SUCCESS" : "BANK_APPLY_FAILED");
			logEntry.setDetails("AppID: " + appId + " | Details: " + details);
			auditLogRepository.save(logEntry);
		} catch (Exception e) {
			log.error("Cannot save audit log", e);
		}
	}
}
