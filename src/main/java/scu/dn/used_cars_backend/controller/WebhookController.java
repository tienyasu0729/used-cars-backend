package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.installment.BankWebhookRequest;
import scu.dn.used_cars_backend.service.BankIntegrationService;
import scu.dn.used_cars_backend.service.InstallmentService;

// Controller này dùng để nhận webhook từ Bank
// API public, được bảo vệ bằng chữ ký HMAC
@RestController
@RequestMapping("/api/v1/webhook/bank")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

	private final BankIntegrationService bankIntegrationService;
	private final InstallmentService installmentService;

	@PostMapping("/loan-result")
	public ResponseEntity<ApiResponse<Void>> receiveLoanResult(
			@RequestBody String rawPayload, // Lấy raw để verify HMAC
			@RequestHeader("X-Signature") String signature) {
		
		log.info("Received bank webhook for loan result. Signature: {}", signature);

		// B1: Verify chữ ký
		if (!bankIntegrationService.verifyWebhookSignature(rawPayload, signature)) {
			log.warn("Invalid webhook signature!");
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<Void>builder()
					.success(false)
					.code("INVALID_SIGNATURE")
					.message("Chữ ký không hợp lệ.")
					.build());
		}

		// B2: Xử lý logic
		try {
			installmentService.handleBankWebhook(rawPayload);
			return ResponseEntity.ok(ApiResponse.success(null));
		} catch (Exception e) {
			log.error("Error handling webhook", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(ApiResponse.<Void>builder()
							.success(false)
							.code("WEBHOOK_ERROR")
							.message("Lỗi xử lý webhook: " + e.getMessage())
							.build());
		}
	}
}
