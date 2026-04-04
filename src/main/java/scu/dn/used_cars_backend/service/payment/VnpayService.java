package scu.dn.used_cars_backend.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class VnpayService {

	private static final Logger log = LoggerFactory.getLogger(VnpayService.class);
	private static final DateTimeFormatter VNP_CREATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

	private final PaymentGatewayConfigService paymentGatewayConfigService;

	public VnpayService(PaymentGatewayConfigService paymentGatewayConfigService) {
		this.paymentGatewayConfigService = paymentGatewayConfigService;
	}

	public record VnpayPayUrlResult(String paymentUrl, String vnpCreateDate) {
	}

	public VnpayPayUrlResult buildPaymentUrl(PaymentGatewayConfigService.VnpayRuntimeConfig cfg, String txnRef,
			BigDecimal amountVnd, String orderInfo, String ipAddr) {
		long amountMinor = amountVnd.multiply(BigDecimal.valueOf(100)).longValueExact();
		ZonedDateTime zNow = ZonedDateTime.now(VN);
		String createDate = zNow.format(VNP_CREATE);

		String ip = resolveVnpIp(ipAddr);
		String orderType = resolveOrderType();

		Map<String, String> vnpParams = new TreeMap<>();
		vnpParams.put("vnp_Version", "2.1.0");
		vnpParams.put("vnp_Command", "pay");
		vnpParams.put("vnp_TmnCode", cfg.tmnCode());
		vnpParams.put("vnp_Amount", String.valueOf(amountMinor));
		vnpParams.put("vnp_CurrCode", "VND");
		vnpParams.put("vnp_TxnRef", txnRef);
		vnpParams.put("vnp_OrderInfo", orderInfo);
		vnpParams.put("vnp_OrderType", orderType);
		vnpParams.put("vnp_Locale", "vn");
		vnpParams.put("vnp_ReturnUrl", cfg.returnUrl());
		vnpParams.put("vnp_IpAddr", ip);
		vnpParams.put("vnp_CreateDate", createDate);

		List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
		Collections.sort(fieldNames);
		List<String> validNames = new ArrayList<>();
		for (String name : fieldNames) {
			String v = vnpParams.get(name);
			if (v != null && !v.isEmpty()) {
				validNames.add(name);
			}
		}
		StringBuilder hashData = new StringBuilder();
		StringBuilder query = new StringBuilder();
		for (int i = 0; i < validNames.size(); i++) {
			String fieldName = validNames.get(i);
			String fieldValue = vnpParams.get(fieldName);
			String encVal = VnpayFormEncoding.encodeValue(fieldValue);
			if (i > 0) {
				hashData.append('&');
				query.append('&');
			}
			hashData.append(fieldName).append('=').append(encVal);
			query.append(fieldName).append('=').append(encVal);
		}

		String hashPayload = hashData.toString();
		String secureHash = signPayPayload(cfg.hashSecret(), hashPayload);
		String queryUrl = query + "&vnp_SecureHash=" + secureHash;
		if (useSha256ForPayUrl()) {
			queryUrl = queryUrl + "&vnp_SecureHashType=SHA256";
		}
		else {
			queryUrl = queryUrl + "&vnp_SecureHashType=SHA512";
		}

		log.info(
				"VNPay buildPaymentUrl: tmnCode={} txnRef={} amountMinor={} createDate={} ip={} orderType={} hmac={}",
				cfg.tmnCode(), txnRef, amountMinor, createDate, ip, orderType,
				useSha256ForPayUrl() ? "SHA256" : "SHA512");
		log.debug("VNPay hashData: {}", hashPayload);
		log.debug("VNPay vnp_SecureHash: {}", secureHash);

		String paymentUrl = cfg.payUrl() + "?" + queryUrl;
		return new VnpayPayUrlResult(paymentUrl, createDate);
	}

	public boolean verifySignature(Map<String, String> params, String hashSecret) {
		String incoming = params.get("vnp_SecureHash");
		if (incoming == null || incoming.isBlank()) {
			return false;
		}

		Map<String, String> fields = new TreeMap<>();
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
				fields.put(k, v);
			}
		}

		List<String> fieldNames = new ArrayList<>(fields.keySet());
		Collections.sort(fieldNames);
		List<String> validNames = new ArrayList<>();
		for (String name : fieldNames) {
			String v = fields.get(name);
			if (v != null && !v.isEmpty()) {
				validNames.add(name);
			}
		}
		StringBuilder hashData = new StringBuilder();
		for (int i = 0; i < validNames.size(); i++) {
			String fieldName = validNames.get(i);
			String fieldValue = fields.get(fieldName);
			String encVal = VnpayFormEncoding.encodeValue(fieldValue);
			if (i > 0) {
				hashData.append('&');
			}
			hashData.append(fieldName).append('=').append(encVal);
		}

		String payload = hashData.toString();
		String s512 = PaymentHmacUtil.hmacSha512Hex(hashSecret, payload);
		String s256 = PaymentHmacUtil.hmacSha256Hex(hashSecret, payload);
		boolean ok = incoming.equalsIgnoreCase(s512) || incoming.equalsIgnoreCase(s256);
		log.debug("VNPay verifySignature: incomingLen={} match512={} match256={}", incoming.length(),
				incoming.equalsIgnoreCase(s512), incoming.equalsIgnoreCase(s256));
		return ok;
	}

	private String signPayPayload(String hashSecret, String hashPayload) {
		if (useSha256ForPayUrl()) {
			return PaymentHmacUtil.hmacSha256Hex(hashSecret, hashPayload);
		}
		return PaymentHmacUtil.hmacSha512Hex(hashSecret, hashPayload);
	}

	private boolean useSha256ForPayUrl() {
		String v = paymentGatewayConfigService.getOptional(PaymentGatewayConfigService.KEY_VNPAY_HMAC_ALGORITHM);
		if (v.isBlank()) {
			return false;
		}
		String u = v.trim().toUpperCase();
		return "SHA256".equals(u) || "HMACSHA256".equals(u);
	}

	private String resolveOrderType() {
		String t = paymentGatewayConfigService.getOptional(PaymentGatewayConfigService.KEY_VNPAY_ORDER_TYPE);
		return t.isBlank() ? "other" : t.trim();
	}

	private String resolveVnpIp(String ipFromRequest) {
		String s = sanitizeIpAddr(ipFromRequest);
		if (!shouldUseCustomerIpFallback(s)) {
			return s;
		}
		String fb = paymentGatewayConfigService.getOptional(PaymentGatewayConfigService.KEY_VNPAY_CUSTOMER_IP_FALLBACK);
		if (fb.isBlank()) {
			return s;
		}
		return sanitizeIpAddr(fb);
	}

	private static boolean shouldUseCustomerIpFallback(String ip) {
		if (ip == null || ip.isBlank() || "127.0.0.1".equals(ip)) {
			return true;
		}
		if (ip.startsWith("10.")) {
			return true;
		}
		if (ip.startsWith("192.168.")) {
			return true;
		}
		if (ip.startsWith("169.254.")) {
			return true;
		}
		if (ip.startsWith("172.")) {
			String[] p = ip.split("\\.");
			if (p.length >= 2) {
				try {
					int second = Integer.parseInt(p[1]);
					return second >= 16 && second <= 31;
				}
				catch (NumberFormatException e) {
					return true;
				}
			}
		}
		return false;
	}

	private static String sanitizeIpAddr(String ip) {
		if (ip == null || ip.isBlank()) {
			return "127.0.0.1";
		}
		ip = ip.trim();
		if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
			return "127.0.0.1";
		}
		if (ip.startsWith("::ffff:")) {
			return ip.substring(7);
		}
		if (ip.contains(":")) {
			return "127.0.0.1";
		}
		return ip;
	}

}
