package scu.dn.used_cars_backend.dto.payment;

import com.fasterxml.jackson.databind.JsonNode;

public record ZaloPayReturnPayload(
	boolean success,
	String code,
	JsonNode gateway,
	Long depositId
) {
}
