package scu.dn.used_cars_backend.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VnpayMerchantApiService {

	private static final String VNP_VERSION = "2.1.0";
	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final DateTimeFormatter VNP_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final ObjectMapper objectMapper;
	private final RestClient restClient = RestClient.create();

	public JsonNode queryDr(PaymentGatewayConfigService.VnpayMerchantApiConfig cfg, String requestId,
			String txnRef, String payCreateDate, String orderInfo, String gatewayTxnNoOptional, String ipAddr) {
		String createDate = ZonedDateTime.now(VN).format(VNP_TS);
		String ip = ipAddr != null && !ipAddr.isBlank() ? ipAddr.trim() : "127.0.0.1";
		ObjectNode body = objectMapper.createObjectNode();
		body.put("vnp_RequestId", requestId);
		body.put("vnp_Version", VNP_VERSION);
		body.put("vnp_Command", "querydr");
		body.put("vnp_TmnCode", cfg.tmnCode());
		body.put("vnp_TxnRef", txnRef);
		body.put("vnp_OrderInfo", orderInfo != null && !orderInfo.isBlank() ? orderInfo : "Query transaction");
		if (gatewayTxnNoOptional != null && !gatewayTxnNoOptional.isBlank()) {
			body.put("vnp_TransactionNo", gatewayTxnNoOptional.trim());
		}
		body.put("vnp_TransactionDate", payCreateDate);
		body.put("vnp_CreateDate", createDate);
		body.put("vnp_IpAddr", ip);
		String data = VnpayMerchantSigning.queryDrRequestData(requestId, VNP_VERSION, "querydr", cfg.tmnCode(), txnRef,
				payCreateDate, createDate, ip, body.get("vnp_OrderInfo").asText());
		body.put("vnp_SecureHash", PaymentHmacUtil.hmacSha512Hex(cfg.hashSecret(), data));
		JsonNode resp = postJson(cfg.merchantApiUrl(), body);
		VnpayMerchantSigning.assertQueryDrResponseHash(resp, cfg.hashSecret());
		return resp;
	}

	public JsonNode refund(PaymentGatewayConfigService.VnpayMerchantApiConfig cfg, String requestId,
			String txnRef, long amountMinor, String transactionType, String payCreateDate, String gatewayTxnNoOptional,
			String createBy, String orderInfo, String ipAddr) {
		String createDate = ZonedDateTime.now(VN).format(VNP_TS);
		String ip = ipAddr != null && !ipAddr.isBlank() ? ipAddr.trim() : "127.0.0.1";
		String txnNo = gatewayTxnNoOptional != null ? gatewayTxnNoOptional.trim() : "";
		String by = createBy != null && !createBy.isBlank() ? createBy.trim() : "system";
		if (by.length() > 245) {
			by = by.substring(0, 245);
		}
		String info = orderInfo != null && !orderInfo.isBlank() ? orderInfo : "Hoan tien giao dich";
		String amt = String.valueOf(amountMinor);
		ObjectNode body = objectMapper.createObjectNode();
		body.put("vnp_RequestId", requestId);
		body.put("vnp_Version", VNP_VERSION);
		body.put("vnp_Command", "refund");
		body.put("vnp_TmnCode", cfg.tmnCode());
		body.put("vnp_TransactionType", transactionType);
		body.put("vnp_TxnRef", txnRef);
		body.put("vnp_Amount", amt);
		if (!txnNo.isEmpty()) {
			body.put("vnp_TransactionNo", txnNo);
		}
		body.put("vnp_TransactionDate", payCreateDate);
		body.put("vnp_CreateBy", by);
		body.put("vnp_CreateDate", createDate);
		body.put("vnp_IpAddr", ip);
		body.put("vnp_OrderInfo", info);
		String data = VnpayMerchantSigning.refundRequestData(requestId, VNP_VERSION, "refund", cfg.tmnCode(),
				transactionType, txnRef, amt, txnNo, payCreateDate, by, createDate, ip, info);
		body.put("vnp_SecureHash", PaymentHmacUtil.hmacSha512Hex(cfg.hashSecret(), data));
		JsonNode resp = postJson(cfg.merchantApiUrl(), body);
		VnpayMerchantSigning.assertRefundResponseHash(resp, cfg.hashSecret());
		return resp;
	}

	public static String newRequestId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	public JsonNode stripSecureHash(JsonNode src) {
		if (src == null || !src.isObject()) {
			return objectMapper.createObjectNode();
		}
		ObjectNode o = objectMapper.createObjectNode();
		src.fields().forEachRemaining(e -> {
			if (!"vnp_SecureHash".equals(e.getKey())) {
				o.set(e.getKey(), e.getValue());
			}
		});
		return o;
	}

	private JsonNode postJson(String url, ObjectNode body) {
		String json;
		try {
			json = objectMapper.writeValueAsString(body);
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Khong tao duoc body VNPay API.");
		}
		String raw = restClient.post()
				.uri(url)
				.contentType(MediaType.APPLICATION_JSON)
				.body(json)
				.retrieve()
				.body(String.class);
		if (raw == null || raw.isBlank()) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "VNPay Merchant API khong phan hoi.");
		}
		try {
			return objectMapper.readTree(raw);
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Khong doc duoc JSON VNPay API.");
		}
	}

}
