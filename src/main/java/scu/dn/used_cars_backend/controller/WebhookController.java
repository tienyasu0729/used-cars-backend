package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.service.BankIntegrationService;
import scu.dn.used_cars_backend.service.InstallmentService;

@RestController
@RequestMapping("/api/v1/webhook/bank")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

	private final BankIntegrationService bankIntegrationService;
	private final InstallmentService installmentService;
	@Value("${app.credit-service.api-secret:}")
	private String creditApiSecret;

	@PostMapping("/loan-result")
	public ResponseEntity<ApiResponse<Void>> receiveLoanResult(
			@RequestBody String rawPayload,
			@RequestHeader(value = "X-Signature", required = false) String signature,
			@RequestHeader(value = "X-Timestamp", required = false) String timestamp,
			@RequestHeader(value = "X-Api-Secret", required = false) String apiSecret) {

		log.info("Received bank webhook loan-result, hasSignature={}, timestamp={}",
				signature != null && !signature.isBlank(),
				timestamp);

		if (creditApiSecret != null && !creditApiSecret.isBlank()) {
			String provided = apiSecret == null ? "" : apiSecret.trim();
			if (!creditApiSecret.equals(provided)) {
				log.warn("Invalid webhook api secret");
				return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<Void>builder()
						.success(false)
						.code("INVALID_API_SECRET")
						.message("Api secret khong hop le.")
						.build());
			}
		}

		if (!bankIntegrationService.verifyWebhookSignature(rawPayload, signature, timestamp)) {
			log.warn("Invalid webhook signature");
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<Void>builder()
					.success(false)
					.code("INVALID_SIGNATURE")
					.message("Chu ky khong hop le.")
					.build());
		}

		try {
			installmentService.handleBankWebhook(rawPayload);
			return ResponseEntity.ok(ApiResponse.success(null));
		} catch (BusinessException be) {
			if (be.getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
				// Race condition: callback den truoc khi appraise commit bankLoanId.
				// Tra 202 de ben credit co the retry thay vi danh dau fail cung.
				log.warn("Webhook loan-result not mapped yet: {}", be.getMessage());
				return ResponseEntity.status(HttpStatus.ACCEPTED)
						.body(ApiResponse.<Void>builder()
								.success(true)
								.code("WEBHOOK_ACCEPTED_RETRY")
								.message(be.getMessage())
								.build());
			}
			throw be;
		} catch (Exception e) {
			log.error("Error handling bank webhook", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(ApiResponse.<Void>builder()
							.success(false)
							.code("WEBHOOK_ERROR")
							.message("Loi xu ly webhook: " + e.getMessage())
							.build());
		}
	}
}
