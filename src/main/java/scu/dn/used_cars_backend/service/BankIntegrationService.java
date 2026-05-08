package scu.dn.used_cars_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.config.FakeBankProperties;
import scu.dn.used_cars_backend.entity.AuditLog;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.repository.AuditLogRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankIntegrationService {

	public record SubmitLoanResult(String loanId, int statusCode, String responseBody) {
	}

	public record LoanStatusResult(String status, String rejectionReason, String pdfUrl, int statusCode, String responseBody) {
	}

	public static class CreditSyncException extends RuntimeException {
		private final boolean retryable;
		private final Integer httpCode;
		private final String upstreamBody;

		public CreditSyncException(String message, boolean retryable, Integer httpCode, String upstreamBody, Throwable cause) {
			super(message, cause);
			this.retryable = retryable;
			this.httpCode = httpCode;
			this.upstreamBody = upstreamBody;
		}

		public CreditSyncException(String message, boolean retryable, Integer httpCode, String upstreamBody) {
			this(message, retryable, httpCode, upstreamBody, null);
		}

		public boolean isRetryable() {
			return retryable;
		}

		public Integer getHttpCode() {
			return httpCode;
		}

		public String getUpstreamBody() {
			return upstreamBody;
		}
	}

	private final FakeBankProperties properties;
	private final ObjectMapper objectMapper;
	private final AuditLogRepository auditLogRepository;
	@Value("${app.payment.frontend-base-url:http://localhost:5173}")
	private String frontendBaseUrl;
	@Value("${app.credit-service.api-secret:}")
	private String creditApiSecret;

	public String applyLoan(InstallmentApplication app, Long staffId, String staffName) {
		SubmitLoanResult result = submitLoan(app, staffId, staffName, "apply-loan-" + app.getId());
		return result.loanId();
	}

	public SubmitLoanResult submitLoan(InstallmentApplication app, Long staffId, String staffName, String idempotencyKey) {
		String endpoint = null;
		try {
			endpoint = requiredValue(properties.url(), "url");
			String apiKey = requiredValue(properties.apiKey(), "api-key");
			String secret = requiredValue(properties.secret(), "secret");
			String jsonPayload = objectMapper.writeValueAsString(buildLoanPayload(app));
			HttpResponse<String> response = sendSignedPost(endpoint, apiKey, secret, jsonPayload, idempotencyKey);
			int code = response.statusCode();
			String body = safeBody(response.body());
			if (code >= 200 && code < 300) {
				@SuppressWarnings("unchecked")
				Map<String, Object> responseMap = objectMapper.readValue(body, Map.class);
				String loanId = responseMap.get("loanId") == null ? null : responseMap.get("loanId").toString();
				if (loanId == null || loanId.isBlank()) {
					throw new CreditSyncException("Credit-service khong tra loanId.", true, code, trimBody(body));
				}
				saveAuditLog(staffId, staffName, app.getId(), true, body);
				return new SubmitLoanResult(loanId, code, body);
			}
			saveAuditLog(staffId, staffName, app.getId(), false, body);
			throw mapError("Loi submit loan toi credit-service.", code, body);
		} catch (CreditSyncException ex) {
			saveAuditLog(staffId, staffName, app.getId(), false, ex.getMessage());
			throw ex;
		} catch (BusinessException ex) {
			throw ex;
		} catch (Exception ex) {
			String detail = describeConnectionFailure(endpoint, ex);
			saveAuditLog(staffId, staffName, app.getId(), false, detail);
			throw new CreditSyncException("Khong the ket noi credit-service: " + detail, true, null, detail, ex);
		}
	}

	public LoanStatusResult queryLoanStatus(String bankLoanId, String idempotencyKey) {
		String endpoint = null;
		try {
			endpoint = requiredValue(properties.statusEndpoint(), "status-endpoint");
			String apiKey = requiredValue(properties.apiKey(), "api-key");
			String secret = requiredValue(properties.secret(), "secret");

			Map<String, Object> body = new LinkedHashMap<>();
			body.put("loanId", bankLoanId);
			String jsonPayload = objectMapper.writeValueAsString(body);

			HttpResponse<String> response = sendSignedPost(endpoint, apiKey, secret, jsonPayload, idempotencyKey);
			if (response.statusCode() == 404) {
				String getQueryEndpoint = endpoint.contains("?")
						? endpoint + "&loanId=" + bankLoanId
						: endpoint + "?loanId=" + bankLoanId;
				response = sendSignedGet(getQueryEndpoint, apiKey, secret, idempotencyKey + ":GET_QUERY");
			}
			if (response.statusCode() == 404) {
				String slash = endpoint.endsWith("/") ? "" : "/";
				String getPathEndpoint = endpoint + slash + bankLoanId;
				response = sendSignedGet(getPathEndpoint, apiKey, secret, idempotencyKey + ":GET_PATH");
			}

			int code = response.statusCode();
			String responseBody = safeBody(response.body());
			if (code >= 200 && code < 300) {
				@SuppressWarnings("unchecked")
				Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
				String status = stringValue(responseMap.get("status"));
				String reason = stringValue(responseMap.get("rejectionReason"));
				String pdfUrl = stringValue(responseMap.get("pdfUrl"));
				return new LoanStatusResult(status, reason, pdfUrl, code, responseBody);
			}
			throw mapError("Loi query loan status tu credit-service.", code, responseBody);
		} catch (CreditSyncException ex) {
			throw ex;
		} catch (BusinessException ex) {
			throw ex;
		} catch (Exception ex) {
			String detail = describeConnectionFailure(endpoint, ex);
			throw new CreditSyncException("Khong the ket noi credit-service khi query status: " + detail, true, null, detail, ex);
		}
	}

	public boolean verifyWebhookSignature(String payload, String signature) {
		return verifyWebhookSignature(payload, signature, null);
	}

	public boolean verifyWebhookSignature(String payload, String signature, String timestamp) {
		try {
			String provided = signature == null ? "" : signature.trim();
			if (provided.isBlank()) {
				return false;
			}
			String expectedRaw = generateHmacSha256(payload, properties.secret());
			if (secureEqualsHex(expectedRaw, provided)) {
				return true;
			}
			String ts = timestamp == null ? "" : timestamp.trim();
			if (ts.isBlank()) {
				return false;
			}
			String expectedWithTimestamp = generateHmacSha256(payload + "." + ts, properties.secret());
			return secureEqualsHex(expectedWithTimestamp, provided);
		} catch (Exception e) {
			log.error("Error verifying webhook signature", e);
			return false;
		}
	}

	private boolean secureEqualsHex(String expectedHex, String providedHex) {
		byte[] a = expectedHex == null ? new byte[0] : expectedHex.getBytes(StandardCharsets.UTF_8);
		byte[] b = providedHex == null ? new byte[0] : providedHex.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(a, b);
	}

	private HttpResponse<String> sendSignedPost(String endpoint, String apiKey, String secret, String jsonPayload, String idempotencyKey)
			throws Exception {
		long timestamp = Instant.now().getEpochSecond();
		String signature = generateHmacSha256(jsonPayload + "." + timestamp, secret);
		return sendSignedPostInternal(endpoint, apiKey, timestamp, signature, idempotencyKey, jsonPayload, true);
	}

	private HttpResponse<String> sendSignedPostInternal(
			String endpoint,
			String apiKey,
			long timestamp,
			String signature,
			String idempotencyKey,
			String jsonPayload,
			boolean allowFallback) throws Exception {
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(readConnectTimeoutMs()))
				.build();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.timeout(Duration.ofMillis(readTimeoutMs()))
				.header("Content-Type", "application/json")
				.header("X-API-Key", apiKey)
				.header("X-Timestamp", String.valueOf(timestamp))
				.header("X-Signature", signature)
				.header("X-Idempotency-Key", idempotencyKey)
				.header("X-Api-Secret", creditApiSecret == null ? "" : creditApiSecret)
				.POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
				.build();
		try {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (Exception ex) {
			if (!allowFallback) throw ex;
			String fallbackEndpoint = localhostFallback(endpoint);
			if (fallbackEndpoint == null) throw ex;
			return sendSignedPostInternal(fallbackEndpoint, apiKey, timestamp, signature, idempotencyKey, jsonPayload, false);
		}
	}

	private HttpResponse<String> sendSignedGet(String endpoint, String apiKey, String secret, String idempotencyKey)
			throws Exception {
		long timestamp = Instant.now().getEpochSecond();
		String signature = generateHmacSha256("." + timestamp, secret);
		return sendSignedGetInternal(endpoint, apiKey, timestamp, signature, idempotencyKey, true);
	}

	private HttpResponse<String> sendSignedGetInternal(
			String endpoint,
			String apiKey,
			long timestamp,
			String signature,
			String idempotencyKey,
			boolean allowFallback) throws Exception {
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(readConnectTimeoutMs()))
				.build();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.timeout(Duration.ofMillis(readTimeoutMs()))
				.header("Accept", "application/json")
				.header("X-API-Key", apiKey)
				.header("X-Timestamp", String.valueOf(timestamp))
				.header("X-Signature", signature)
				.header("X-Idempotency-Key", idempotencyKey)
				.header("X-Api-Secret", creditApiSecret == null ? "" : creditApiSecret)
				.GET()
				.build();
		try {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (Exception ex) {
			if (!allowFallback) throw ex;
			String fallbackEndpoint = localhostFallback(endpoint);
			if (fallbackEndpoint == null) throw ex;
			return sendSignedGetInternal(fallbackEndpoint, apiKey, timestamp, signature, idempotencyKey, false);
		}
	}

	private String localhostFallback(String endpoint) {
		try {
			URI uri = URI.create(endpoint);
			String host = uri.getHost();
			if (host == null) return null;
			String fallbackHost = null;
			if ("localhost".equalsIgnoreCase(host)) {
				fallbackHost = "127.0.0.1";
			} else if ("127.0.0.1".equals(host)) {
				fallbackHost = "host.docker.internal";
			} else if ("host.docker.internal".equalsIgnoreCase(host)) {
				fallbackHost = "localhost";
			}
			if (fallbackHost == null) return null;
			URI fallback = new URI(
					uri.getScheme(),
					uri.getUserInfo(),
					fallbackHost,
					uri.getPort(),
					uri.getPath(),
					uri.getQuery(),
					uri.getFragment());
			return fallback.toString();
		} catch (Exception ex) {
			return null;
		}
	}

	private String describeConnectionFailure(String endpoint, Exception ex) {
		String errorType = ex.getClass().getSimpleName();
		String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			Throwable cause = ex.getCause();
			message = cause == null ? null : cause.getMessage();
		}
		if (message == null || message.isBlank()) {
			message = "khong co chi tiet tu JVM";
		}
		String target = endpoint == null || endpoint.isBlank() ? "unknown endpoint" : endpoint;
		return target + " -> " + errorType + ": " + message;
	}

	private Map<String, Object> buildLoanPayload(InstallmentApplication app) {
		Map<String, Object> payload = new LinkedHashMap<>();
		BigDecimal resolvedAmount = resolveAmount(app);
		long amountLong = resolvedAmount.max(BigDecimal.ZERO).longValue();
		String fullName = requiredPayloadString(app.getFullName(), "fullName");
		String email = requiredPayloadString(app.getEmail(), "email");
		String phone = requiredPayloadString(app.getPhoneNumber(), "phoneNumber");
		String bankCode = requiredPayloadString(app.getBankCode(), "bankCode");
		String identityNumber = requiredPayloadString(app.getIdentityNumber(), "identityNumber");
		String vehicleModel = app.getVehicle() != null ? safeTrim(app.getVehicle().getTitle()) : null;
		if (vehicleModel == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thieu thong tin vehicleModel.");
		}
		if (app.getVehiclePrice() == null || app.getVehiclePrice().compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Vehicle price phai lon hon 0 truoc khi tham dinh.");
		}
		if (amountLong <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Loan amount phai lon hon 0 truoc khi tham dinh.");
		}
		Long loanTermMonths = app.getLoanTermMonths() == null ? null : app.getLoanTermMonths().longValue();
		if (loanTermMonths == null || loanTermMonths <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thieu hoac sai loanTermMonths.");
		}
		String cccdUrl = resolveCccdDocumentUrl(app);
		Map<String, Object> documentsPayload = buildDocumentsPayload(app, cccdUrl);
		Map<String, Object> step1Personal = buildStep1Personal(app);
		Map<String, Object> step2Occupation = buildStep2Occupation(app);
		Map<String, Object> step3Finance = buildStep3Finance(app);
		Map<String, Object> step4Loan = buildStep4Loan(app, amountLong, loanTermMonths);
		Map<String, Object> step5Documents = buildStep5Documents(app, cccdUrl);
		Map<String, Object> step6Commitment = buildStep6Commitment(app);
		Map<String, Object> vehicleSnapshot = buildVehicleSnapshot(app);
		Map<String, Object> step7Confirmation = buildStep7Confirmation(app, vehicleSnapshot);
		Map<String, Object> applicationSnapshot = buildApplicationSnapshot(
				app,
				step1Personal,
				step2Occupation,
				step3Finance,
				step4Loan,
				step5Documents,
				step6Commitment,
				step7Confirmation,
				vehicleSnapshot,
				documentsPayload);

		payload.put("customerName", fullName);
		payload.put("externalId", app.getId() != null ? app.getId().toString() : null);
		payload.put("applicationId", app.getId());
		payload.put("customerEmail", email);
		payload.put("customerPhone", phone);
		payload.put("phone", phone);
		payload.put("vehicleModel", vehicleModel);
		payload.put("vehiclePrice", app.getVehiclePrice());
		payload.put("loanAmount", amountLong);
		payload.put("amount", amountLong);
		payload.put("bankCode", bankCode);
		payload.put("loanTermMonths", loanTermMonths);
		payload.put("term", loanTermMonths);
		payload.put("identityNumber", identityNumber);
		payload.put("cccd", identityNumber);
		payload.put("fullName", fullName);
		payload.put("phoneNumber", phone);
		payload.put("email", email);
		payload.put("documents", documentsPayload);
		payload.put("applicationSnapshot", applicationSnapshot);
		payload.put("step1Personal", step1Personal);
		payload.put("step2Occupation", step2Occupation);
		payload.put("step3Finance", step3Finance);
		payload.put("step4Loan", step4Loan);
		payload.put("step5Documents", step5Documents);
		payload.put("step6Commitment", step6Commitment);
		payload.put("step7Confirmation", step7Confirmation);
		payload.put("vehicleSnapshot", vehicleSnapshot);
		payload.put("vehicle", vehicleSnapshot);
		payload.put("createdAt", app.getCreatedAt());
		payload.put("updatedAt", app.getUpdatedAt());
		return payload;
	}

	private Map<String, Object> buildDocumentsPayload(InstallmentApplication app, String cccdUrl) {
		Map<String, Object> documents = new LinkedHashMap<>();
		List<Map<String, Object>> allDocuments = buildAllDocuments(app);
		Map<String, List<String>> byType = buildDocumentsByType(allDocuments);
		documents.put("cccdUrl", cccdUrl);
		documents.put("allDocuments", allDocuments);
		documents.put("byType", byType);
		return documents;
	}

	private List<Map<String, Object>> buildAllDocuments(InstallmentApplication app) {
		List<Map<String, Object>> all = new ArrayList<>();
		List<scu.dn.used_cars_backend.entity.InstallmentDocument> docs = app.getDocuments();
		if (docs == null) {
			return all;
		}
		for (scu.dn.used_cars_backend.entity.InstallmentDocument d : docs) {
			if (d == null) continue;
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("type", safeTrim(d.getDocumentType()));
			row.put("url", safeTrim(d.getDocumentUrl()));
			row.put("fileName", safeTrim(d.getOriginalFileName()));
			row.put("uploadedAt", d.getUploadedAt());
			all.add(row);
		}
		return all;
	}

	private Map<String, List<String>> buildDocumentsByType(List<Map<String, Object>> allDocuments) {
		Map<String, List<String>> byType = new HashMap<>();
		for (Map<String, Object> doc : allDocuments) {
			String type = doc.get("type") == null ? "UNKNOWN" : String.valueOf(doc.get("type"));
			String url = doc.get("url") == null ? null : String.valueOf(doc.get("url"));
			if (!isValidHttpUrl(url)) {
				continue;
			}
			byType.computeIfAbsent(type, k -> new ArrayList<>()).add(url);
		}
		return byType;
	}

	private Map<String, Object> buildApplicationSnapshot(
			InstallmentApplication app,
			Map<String, Object> step1Personal,
			Map<String, Object> step2Occupation,
			Map<String, Object> step3Finance,
			Map<String, Object> step4Loan,
			Map<String, Object> step5Documents,
			Map<String, Object> step6Commitment,
			Map<String, Object> step7Confirmation,
			Map<String, Object> vehicleSnapshot,
			Map<String, Object> documentsPayload) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("id", app.getId());
		snapshot.put("applicationId", app.getId());
		snapshot.put("customerId", app.getCustomer() != null ? app.getCustomer().getId() : null);
		snapshot.put("vehicleId", app.getVehicle() != null ? app.getVehicle().getId() : null);
		snapshot.put("bankLoanId", safeTrim(app.getBankLoanId()));
		snapshot.put("bankCode", safeTrim(app.getBankCode()));
		snapshot.put("status", app.getStatus() != null ? app.getStatus().name() : null);
		snapshot.put("rejectionReason", safeTrim(app.getRejectionReason()));
		snapshot.put("bankPdfUrl", safeTrim(app.getBankPdfUrl()));
		snapshot.put("fullName", safeTrim(app.getFullName()));
		snapshot.put("identityNumber", safeTrim(app.getIdentityNumber()));
		snapshot.put("phoneNumber", safeTrim(app.getPhoneNumber()));
		snapshot.put("email", safeTrim(app.getEmail()));
		snapshot.put("dob", app.getDob());
		snapshot.put("identityIssuedDate", app.getIdentityIssuedDate());
		snapshot.put("identityIssuedPlace", safeTrim(app.getIdentityIssuedPlace()));
		snapshot.put("permanentAddress", safeTrim(app.getPermanentAddress()));
		snapshot.put("currentAddress", safeTrim(app.getCurrentAddress()));
		snapshot.put("employmentType", safeTrim(app.getEmploymentType()));
		snapshot.put("companyName", safeTrim(app.getCompanyName()));
		snapshot.put("jobTitle", safeTrim(app.getJobTitle()));
		snapshot.put("workDuration", safeTrim(app.getWorkDuration()));
		snapshot.put("salaryMethod", safeTrim(app.getSalaryMethod()));
		snapshot.put("businessName", safeTrim(app.getBusinessName()));
		snapshot.put("businessType", safeTrim(app.getBusinessType()));
		snapshot.put("businessDuration", safeTrim(app.getBusinessDuration()));
		snapshot.put("monthlyIncome", app.getMonthlyIncome());
		snapshot.put("monthlyExpenses", app.getMonthlyExpenses());
		snapshot.put("existingLoans", app.getExistingLoans());
		snapshot.put("dependentsCount", app.getDependentsCount());
		snapshot.put("vehiclePrice", app.getVehiclePrice());
		snapshot.put("prepaymentPercent", app.getPrepaymentPercent());
		snapshot.put("prepaymentAmount", app.getPrepaymentAmount());
		snapshot.put("loanAmount", app.getLoanAmount());
		snapshot.put("loanTermMonths", app.getLoanTermMonths());
		snapshot.put("repaymentMethod", safeTrim(app.getRepaymentMethod()));
		snapshot.put("requestPreDeposit", app.getRequestPreDeposit());
		snapshot.put("agreedTerms", app.getAgreedTerms());
		snapshot.put("agreedPrivacy", app.getAgreedPrivacy());
		snapshot.put("signatureUrl", safeTrim(app.getSignatureUrl()));
		snapshot.put("signedDate", app.getSignedDate());
		snapshot.put("documents", documentsPayload);
		snapshot.put("step1Personal", step1Personal);
		snapshot.put("step2Occupation", step2Occupation);
		snapshot.put("step3Finance", step3Finance);
		snapshot.put("step4Loan", step4Loan);
		snapshot.put("step5Documents", step5Documents);
		snapshot.put("step6Commitment", step6Commitment);
		snapshot.put("step7Confirmation", step7Confirmation);
		snapshot.put("vehicleSnapshot", vehicleSnapshot);
		snapshot.put("vehicle", vehicleSnapshot);
		snapshot.put("createdAt", app.getCreatedAt());
		snapshot.put("updatedAt", app.getUpdatedAt());
		return snapshot;
	}

	private Map<String, Object> buildStep1Personal(InstallmentApplication app) {
		Map<String, Object> step = new LinkedHashMap<>();
		step.put("fullName", safeTrim(app.getFullName()));
		step.put("cccd", safeTrim(app.getIdentityNumber()));
		step.put("identityNumber", safeTrim(app.getIdentityNumber()));
		step.put("dob", app.getDob());
		step.put("identityIssuedDate", app.getIdentityIssuedDate());
		step.put("identityIssuedPlace", safeTrim(app.getIdentityIssuedPlace()));
		step.put("permanentAddress", safeTrim(app.getPermanentAddress()));
		step.put("currentAddress", safeTrim(app.getCurrentAddress()));
		step.put("phoneNumber", safeTrim(app.getPhoneNumber()));
		step.put("email", safeTrim(app.getEmail()));
		return step;
	}

	private Map<String, Object> buildStep2Occupation(InstallmentApplication app) {
		Map<String, Object> step = new LinkedHashMap<>();
		step.put("employmentType", safeTrim(app.getEmploymentType()));
		step.put("companyName", safeTrim(app.getCompanyName()));
		step.put("jobTitle", safeTrim(app.getJobTitle()));
		step.put("workDuration", safeTrim(app.getWorkDuration()));
		step.put("salaryMethod", safeTrim(app.getSalaryMethod()));
		step.put("businessName", safeTrim(app.getBusinessName()));
		step.put("businessType", safeTrim(app.getBusinessType()));
		step.put("businessDuration", safeTrim(app.getBusinessDuration()));
		return step;
	}

	private Map<String, Object> buildStep3Finance(InstallmentApplication app) {
		Map<String, Object> step = new LinkedHashMap<>();
		step.put("monthlyIncome", app.getMonthlyIncome());
		step.put("monthlyExpenses", app.getMonthlyExpenses());
		step.put("existingLoans", app.getExistingLoans());
		step.put("dependentsCount", app.getDependentsCount());
		return step;
	}

	private Map<String, Object> buildStep4Loan(InstallmentApplication app, long amountLong, Long loanTermMonths) {
		Map<String, Object> step = new LinkedHashMap<>();
		step.put("vehiclePrice", app.getVehiclePrice());
		step.put("prepaymentPercent", app.getPrepaymentPercent());
		step.put("prepaymentAmount", app.getPrepaymentAmount());
		step.put("loanAmount", amountLong);
		step.put("loanTermMonths", loanTermMonths);
		step.put("term", loanTermMonths);
		step.put("repaymentMethod", safeTrim(app.getRepaymentMethod()));
		step.put("bankCode", safeTrim(app.getBankCode()));
		step.put("requestPreDeposit", app.getRequestPreDeposit());
		return step;
	}

	private Map<String, Object> buildStep5Documents(InstallmentApplication app, String cccdUrl) {
		Map<String, Object> step = new LinkedHashMap<>();
		List<Map<String, Object>> allDocuments = buildAllDocuments(app);
		step.put("cccdUrl", cccdUrl);
		step.put("allDocuments", allDocuments);
		step.put("byType", buildDocumentsByType(allDocuments));
		return step;
	}

	private Map<String, Object> buildStep6Commitment(InstallmentApplication app) {
		Map<String, Object> step = new LinkedHashMap<>();
		step.put("agreedTerms", app.getAgreedTerms());
		step.put("agreedPrivacy", app.getAgreedPrivacy());
		step.put("signatureUrl", safeTrim(app.getSignatureUrl()));
		step.put("signedDate", app.getSignedDate());
		return step;
	}

	private Map<String, Object> buildStep7Confirmation(InstallmentApplication app, Map<String, Object> vehicleSnapshot) {
		Map<String, Object> step = new LinkedHashMap<>();
		step.put("status", app.getStatus() != null ? app.getStatus().name() : null);
		step.put("bankLoanId", safeTrim(app.getBankLoanId()));
		step.put("rejectionReason", safeTrim(app.getRejectionReason()));
		step.put("bankPdfUrl", safeTrim(app.getBankPdfUrl()));
		step.put("updatedAt", app.getUpdatedAt());
		step.put("vehicleSnapshot", vehicleSnapshot);
		step.put("vehicle", vehicleSnapshot);
		return step;
	}

	private Map<String, Object> buildVehicleSnapshot(InstallmentApplication app) {
		Map<String, Object> vehicle = new LinkedHashMap<>();
		if (app.getVehicle() == null) {
			return vehicle;
		}
		vehicle.put("id", app.getVehicle().getId());
		vehicle.put("listingId", safeTrim(app.getVehicle().getListingId()));
		vehicle.put("title", safeTrim(app.getVehicle().getTitle()));
		vehicle.put("price", app.getVehicle().getPrice());
		vehicle.put("year", app.getVehicle().getYear());
		vehicle.put("fuel", safeTrim(app.getVehicle().getFuel()));
		vehicle.put("transmission", safeTrim(app.getVehicle().getTransmission()));
		vehicle.put("mileage", app.getVehicle().getMileage());
		vehicle.put("bodyStyle", safeTrim(app.getVehicle().getBodyStyle()));
		vehicle.put("origin", safeTrim(app.getVehicle().getOrigin()));
		vehicle.put("status", safeTrim(app.getVehicle().getStatus()));
		vehicle.put("createdAt", app.getVehicle().getCreatedAt());
		vehicle.put("updatedAt", app.getVehicle().getUpdatedAt());
		String base = safeTrim(frontendBaseUrl);
		if (base != null) {
			base = base.replaceAll("/$", "");
			vehicle.put("detailUrl", base + "/vehicles/" + app.getVehicle().getId());
		}
		return vehicle;
	}

	private String requiredPayloadString(String value, String field) {
		String trimmed = safeTrim(value);
		if (trimmed == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thieu thong tin " + field + " truoc khi tham dinh.");
		}
		return trimmed;
	}

	private String resolveCccdDocumentUrl(InstallmentApplication app) {
		List<scu.dn.used_cars_backend.entity.InstallmentDocument> docs = app.getDocuments();
		if (docs == null || docs.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thieu tai lieu CCCD (documents.cccdUrl).");
		}
		for (scu.dn.used_cars_backend.entity.InstallmentDocument d : docs) {
			if (d == null) continue;
			String type = safeTrim(d.getDocumentType());
			String url = safeTrim(d.getDocumentUrl());
			if (url == null || type == null) continue;
			String t = type.toLowerCase();
			if ((t.contains("cccd") || t.contains("cmnd") || t.contains("identity")) && isValidHttpUrl(url)) {
				return url;
			}
		}
		throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thieu tai lieu CCCD (documents.cccdUrl).");
	}

	private boolean isValidHttpUrl(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		try {
			URI uri = new URI(value.trim());
			String scheme = uri.getScheme();
			if (scheme == null) return false;
			String s = scheme.toLowerCase();
			return ("http".equals(s) || "https".equals(s)) && uri.getHost() != null && !uri.getHost().isBlank();
		} catch (URISyntaxException ex) {
			return false;
		}
	}

	private String safeTrim(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private BigDecimal resolveAmount(InstallmentApplication app) {
		if (app.getLoanAmount() != null && app.getLoanAmount().compareTo(BigDecimal.ZERO) > 0) {
			return app.getLoanAmount();
		}
		BigDecimal vehiclePrice = app.getVehiclePrice();
		BigDecimal prepaymentAmount = app.getPrepaymentAmount();
		if (vehiclePrice != null && prepaymentAmount != null) {
			BigDecimal fallback = vehiclePrice.subtract(prepaymentAmount);
			if (fallback.compareTo(BigDecimal.ZERO) > 0) {
				return fallback;
			}
		}
		return BigDecimal.ZERO;
	}

	private CreditSyncException mapError(String message, int httpCode, String body) {
		boolean retryable = httpCode >= 500 || httpCode == 408 || httpCode == 429;
		return new CreditSyncException(message + " HTTP " + httpCode, retryable, httpCode, trimBody(body));
	}

	private String requiredValue(String value, String field) {
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.isBlank()) {
			throw new BusinessException(ErrorCode.BANK_API_ERROR, "Thieu cau hinh credit-service (" + field + ").");
		}
		return trimmed;
	}

	private int readTimeoutMs() {
		Integer timeout = properties.timeoutMs();
		return timeout == null || timeout <= 0 ? 10000 : timeout;
	}

	private int readConnectTimeoutMs() {
		Integer timeout = properties.connectTimeoutMs();
		return timeout == null || timeout <= 0 ? 3000 : timeout;
	}

	private String safeBody(String body) {
		return body == null ? "" : body;
	}

	private String stringValue(Object value) {
		return value == null ? null : value.toString();
	}

	private String trimBody(String body) {
		String normalized = body == null ? "" : body.replaceAll("\\s+", " ").trim();
		if (normalized.length() > 500) {
			return normalized.substring(0, 500) + "...";
		}
		return normalized;
	}

	private String generateHmacSha256(String data, String key) throws Exception {
		Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
		SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		sha256_HMAC.init(secret_key);
		byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		for (byte b : hash) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	private void saveAuditLog(Long staffId, String staffName, Long appId, boolean success, String response) {
		try {
			AuditLog logEntry = new AuditLog();
			logEntry.setUserId(staffId);
			logEntry.setUserName(staffName);
			logEntry.setModule("INSTALLMENT");
			logEntry.setAction("BANK_API_CALL");
			logEntry.setDetails("AppID: " + appId
					+ " | Result: " + (success ? "SUCCESS" : "FAILED")
					+ " | Response: " + response);
			auditLogRepository.save(logEntry);
		} catch (Exception e) {
			log.error("Cannot save audit log", e);
		}
	}
}
