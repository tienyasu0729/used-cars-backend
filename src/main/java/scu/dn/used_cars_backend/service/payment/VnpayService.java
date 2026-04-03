package scu.dn.used_cars_backend.service.payment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class VnpayService {

	private static final DateTimeFormatter VNP_CREATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

	public record VnpayPayUrlResult(String paymentUrl, String vnpCreateDate) {
	}

	public VnpayPayUrlResult buildPaymentUrl(PaymentGatewayConfigService.VnpayRuntimeConfig cfg, String txnRef,
			BigDecimal amountVnd, String orderInfo, String ipAddr) {
		long amountMinor = amountVnd.multiply(BigDecimal.valueOf(100)).longValueExact();
		String createDate = ZonedDateTime.now(VN).format(VNP_CREATE);
		TreeMap<String, String> fields = new TreeMap<>();
		fields.put("vnp_Version", "2.1.0");
		fields.put("vnp_Command", "pay");
		fields.put("vnp_TmnCode", cfg.tmnCode());
		fields.put("vnp_Amount", String.valueOf(amountMinor));
		fields.put("vnp_CurrCode", "VND");
		fields.put("vnp_TxnRef", txnRef);
		fields.put("vnp_OrderInfo", orderInfo);
		fields.put("vnp_OrderType", "other");
		fields.put("vnp_Locale", "vn");
		fields.put("vnp_ReturnUrl", cfg.returnUrl());
		fields.put("vnp_IpnUrl", cfg.ipnUrl());
		fields.put("vnp_CreateDate", createDate);
		fields.put("vnp_IpAddr", ipAddr != null && !ipAddr.isBlank() ? ipAddr : "127.0.0.1");
		String hashData = buildHashData(fields);
		String secureHash = PaymentHmacUtil.hmacSha512Hex(cfg.hashSecret(), hashData);
		fields.put("vnp_SecureHash", secureHash);
		String query = fields.entrySet().stream()
				.map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
		String base = cfg.payUrl();
		String url = base.contains("?") ? base + "&" + query : base + "?" + query;
		return new VnpayPayUrlResult(url, createDate);
	}

	public boolean verifySignature(Map<String, String> params, String hashSecret) {
		TreeMap<String, String> copy = new TreeMap<>();
		for (Map.Entry<String, String> e : params.entrySet()) {
			String k = e.getKey();
			if (k == null) {
				continue;
			}
			if ("vnp_SecureHash".equalsIgnoreCase(k) || "vnp_SecureHashType".equalsIgnoreCase(k)) {
				continue;
			}
			String v = e.getValue();
			if (v != null && !v.isEmpty()) {
				copy.put(k, v);
			}
		}
		String incoming = params.get("vnp_SecureHash");
		if (incoming == null) {
			return false;
		}
		String computed = PaymentHmacUtil.hmacSha512Hex(hashSecret, buildHashData(copy));
		return incoming.equalsIgnoreCase(computed);
	}

	private static String buildHashData(TreeMap<String, String> sorted) {
		return sorted.entrySet().stream()
				.map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
	}
}
