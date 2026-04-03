package scu.dn.used_cars_backend.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ZaloPayService {

	private final ObjectMapper objectMapper;
	private final RestClient restClient = RestClient.create();

	public String createOrderAndGetPayUrl(PaymentGatewayConfigService.ZaloPayRuntimeConfig cfg, String appTransId,
			long amountVnd, String appUser, String description) {
		long appTime = Instant.now().toEpochMilli();
		String embedData = "{}";
		String item = "[]";
		int appId = parseAppId(cfg.appId());
		String macData = cfg.appId() + "|" + appTransId + "|" + appUser + "|" + amountVnd + "|" + appTime + "|"
				+ embedData + "|" + item;
		String mac = PaymentHmacUtil.hmacSha256Hex(cfg.key1(), macData);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("app_id", appId);
		body.put("app_trans_id", appTransId);
		body.put("app_user", appUser);
		body.put("amount", amountVnd);
		body.put("app_time", appTime);
		body.put("embed_data", embedData);
		body.put("item", item);
		body.put("description", description != null ? description : "Thanh toan don hang");
		body.put("bank_code", "");
		body.put("callback_url", cfg.callbackUrl());
		body.put("mac", mac);
		String json;
		try {
			json = objectMapper.writeValueAsString(body);
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo được body ZaloPay.");
		}
		String raw = restClient.post()
				.uri(cfg.endpoint())
				.contentType(MediaType.APPLICATION_JSON)
				.body(json)
				.retrieve()
				.body(String.class);
		if (raw == null) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "ZaloPay không phản hồi.");
		}
		try {
			JsonNode root = objectMapper.readTree(raw);
			int rc = root.path("return_code").asInt(-1);
			if (rc != 1) {
				String msg = root.path("return_message").asText("Loi ZaloPay");
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, msg);
			}
			return root.path("order_url").asText(null);
		}
		catch (BusinessException e) {
			throw e;
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không đọc được phản hồi ZaloPay.");
		}
	}

	public boolean verifyCallbackMac(String dataRaw, String mac, String key2) {
		if (dataRaw == null || mac == null || key2 == null) {
			return false;
		}
		String computed = PaymentHmacUtil.hmacSha256Hex(key2, dataRaw);
		return mac.equalsIgnoreCase(computed);
	}

	public JsonNode parseCallbackDataJson(String dataRaw) {
		try {
			return objectMapper.readTree(dataRaw);
		}
		catch (Exception e) {
			return objectMapper.createObjectNode();
		}
	}

	private static int parseAppId(String appId) {
		try {
			return Integer.parseInt(appId.trim());
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "zalopay_app_id không hợp lệ.");
		}
	}
}
