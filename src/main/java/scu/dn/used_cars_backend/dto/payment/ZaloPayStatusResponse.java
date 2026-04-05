package scu.dn.used_cars_backend.dto.payment;

import com.fasterxml.jackson.databind.JsonNode;

public record ZaloPayStatusResponse(JsonNode gateway, String localStatus, boolean synced, Long orderPaymentId,
		Long depositId) {
}
