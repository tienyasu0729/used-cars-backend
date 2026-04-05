package scu.dn.used_cars_backend.service.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.config.PaymentGatewayProperties;
import scu.dn.used_cars_backend.repository.SystemConfigRepository;

@Service
@RequiredArgsConstructor
public class PaymentGatewayConfigService {

	public static final String KEY_VNPAY_TMN = "vnpay_tmn_code";
	public static final String KEY_VNPAY_HASH_SECRET = "vnpay_hash_secret";
	public static final String KEY_VNPAY_ORDER_TYPE = "vnpay_order_type";
	public static final String KEY_VNPAY_HMAC_ALGORITHM = "vnpay_hmac_algorithm";
	public static final String KEY_VNPAY_PAY_URL = "vnpay_pay_url";
	public static final String KEY_VNPAY_RETURN_URL = "vnpay_return_url";
	public static final String KEY_VNPAY_IPN_URL = "vnpay_ipn_url";
	public static final String KEY_VNPAY_ENABLED = "vnpay_enabled";
	public static final String KEY_VNPAY_MERCHANT_API_URL = "vnpay_merchant_api_url";
	public static final String KEY_VNPAY_CUSTOMER_IP_FALLBACK = "vnpay_customer_ip_fallback";

	public static final String KEY_DEPOSIT_ONLINE_PAYMENT_TIMEOUT_MINUTES = "deposit_online_payment_timeout_minutes";

	public static final String DEFAULT_VNPAY_MERCHANT_API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";

	public static final String KEY_ZALO_APP_ID = "zalopay_app_id";
	public static final String KEY_ZALO_KEY1 = "zalopay_key1";
	public static final String KEY_ZALO_KEY2 = "zalopay_key2";
	public static final String KEY_ZALO_ENDPOINT = "zalopay_endpoint";
	public static final String KEY_ZALO_CALLBACK_URL = "zalopay_callback_url";
	public static final String KEY_ZALO_ENABLED = "zalopay_enabled";

	/** Bật ghi nhận tiền mặt (cọc / thu tay) cho staff & quản lý. Khách đặt cọc qua web không dùng flag này. */
	public static final String KEY_CASH_ENABLED = "cash_enabled";

	public static final String KEY_APP_FRONTEND_BASE_URL = "app_frontend_base_url";

	private final SystemConfigRepository systemConfigRepository;
	private final PaymentGatewayProperties paymentGatewayProperties;

	@Transactional(readOnly = true)
	public String requireNonBlank(String key) {
		String v = getOptionalFromDb(key);
		if (v.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thiếu cấu hình: " + key);
		}
		return v;
	}

	@Transactional(readOnly = true)
	public String getOptional(String key) {
		String y = yamlFirst(key);
		if (y != null) {
			return y;
		}
		return getOptionalFromDb(key);
	}

	@Transactional(readOnly = true)
	String getOptionalFromDb(String key) {
		return systemConfigRepository.findByConfigKey(key)
				.map(r -> r.getConfigValue() != null ? r.getConfigValue().trim() : "")
				.orElse("");
	}

	private String yamlFirst(String key) {
		PaymentGatewayProperties.Vnpay vn = paymentGatewayProperties.getVnpay();
		PaymentGatewayProperties.Zalopay zp = paymentGatewayProperties.getZalopay();
		return switch (key) {
			case KEY_VNPAY_PAY_URL -> nonBlankOrNull(vn.getPayUrl());
			case KEY_VNPAY_RETURN_URL -> nonBlankOrNull(vn.getReturnUrl());
			case KEY_VNPAY_IPN_URL -> nonBlankOrNull(vn.getIpnUrl());
			case KEY_VNPAY_MERCHANT_API_URL -> nonBlankOrNull(vn.getMerchantApiUrl());
			case KEY_VNPAY_ORDER_TYPE -> nonBlankOrNull(vn.getOrderType());
			case KEY_VNPAY_HMAC_ALGORITHM -> nonBlankOrNull(vn.getHmacAlgorithm());
			case KEY_VNPAY_CUSTOMER_IP_FALLBACK -> nonBlankOrNull(vn.getCustomerIpFallback());
			case KEY_ZALO_ENDPOINT -> nonBlankOrNull(zp.getEndpoint());
			case KEY_ZALO_CALLBACK_URL -> nonBlankOrNull(zp.getCallbackUrl());
			default -> null;
		};
	}

	private static String nonBlankOrNull(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		return s.trim();
	}

	public static String normalizeVnpHashSecret(String raw) {
		if (raw == null) {
			return "";
		}
		String t = raw.trim();
		if (!t.isEmpty() && t.charAt(0) == '\uFEFF') {
			t = t.substring(1).trim();
		}
		return t;
	}

	@Transactional(readOnly = true)
	public String requireVnpayHashSecret() {
		String y = normalizeVnpHashSecret(paymentGatewayProperties.getVnpay().getHashSecret());
		if (!y.isEmpty()) {
			return y;
		}
		String db = normalizeVnpHashSecret(getOptionalFromDb(KEY_VNPAY_HASH_SECRET));
		if (db.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Thiếu cấu hình VNPay hash secret (app.payment.vnpay.hash-secret hoặc DB vnpay_hash_secret).");
		}
		return db;
	}

	private String requireYamlOrDb(String yamlValue, String dbKey, String label) {
		String y = yamlValue != null ? yamlValue.trim() : "";
		if (!y.isEmpty()) {
			return y;
		}
		String d = getOptionalFromDb(dbKey).trim();
		if (d.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thiếu cấu hình " + label + ".");
		}
		return d;
	}

	@Transactional(readOnly = true)
	public boolean isTruthy(String key) {
		String v = getOptionalFromDb(key);
		return "true".equalsIgnoreCase(v) || "1".equals(v);
	}

	@Transactional(readOnly = true)
	public void assertVnpayEnabled() {
		if (!isTruthy(KEY_VNPAY_ENABLED)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "VNPay chưa bật trong cấu hình.");
		}
	}

	@Transactional(readOnly = true)
	public void assertZaloPayEnabled() {
		if (!isTruthy(KEY_ZALO_ENABLED)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ZaloPay chưa bật trong cấu hình.");
		}
	}

	/**
	 * Tiền mặt cho nội bộ. Mặc định bật nếu chưa có key trong DB (tương thích hệ thống cũ).
	 */
	@Transactional(readOnly = true)
	public boolean isCashPaymentAllowed() {
		String v = getOptionalFromDb(KEY_CASH_ENABLED);
		if (v == null || v.isBlank()) {
			return true;
		}
		return isTruthy(KEY_CASH_ENABLED);
	}

	@Transactional(readOnly = true)
	public void assertCashPaymentAllowed() {
		if (!isCashPaymentAllowed()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tiền mặt chưa bật trong cấu hình hệ thống.");
		}
	}

	@Transactional(readOnly = true)
	public VnpayRuntimeConfig loadVnpayForCreate() {
		assertVnpayEnabled();
		PaymentGatewayProperties.Vnpay vn = paymentGatewayProperties.getVnpay();
		return new VnpayRuntimeConfig(
				requireYamlOrDb(vn.getTmnCode(), KEY_VNPAY_TMN, "VNPay TMN"),
				requireVnpayHashSecret(),
				requireYamlOrDb(vn.getPayUrl(), KEY_VNPAY_PAY_URL, "VNPay pay URL"),
				requireYamlOrDb(vn.getReturnUrl(), KEY_VNPAY_RETURN_URL, "VNPay return URL"),
				requireYamlOrDb(vn.getIpnUrl(), KEY_VNPAY_IPN_URL, "VNPay IPN URL"));
	}

	@Transactional(readOnly = true)
	public VnpayRuntimeConfig loadVnpayForVerify() {
		PaymentGatewayProperties.Vnpay vn = paymentGatewayProperties.getVnpay();
		return new VnpayRuntimeConfig(
				requireYamlOrDb(vn.getTmnCode(), KEY_VNPAY_TMN, "VNPay TMN"),
				requireVnpayHashSecret(),
				getOptional(KEY_VNPAY_PAY_URL),
				getOptional(KEY_VNPAY_RETURN_URL),
				getOptional(KEY_VNPAY_IPN_URL));
	}

	@Transactional(readOnly = true)
	public VnpayMerchantApiConfig loadVnpayForMerchantApi() {
		assertVnpayEnabled();
		PaymentGatewayProperties.Vnpay vn = paymentGatewayProperties.getVnpay();
		String url = getOptional(KEY_VNPAY_MERCHANT_API_URL);
		if (url.isBlank()) {
			url = DEFAULT_VNPAY_MERCHANT_API_URL;
		}
		return new VnpayMerchantApiConfig(requireYamlOrDb(vn.getTmnCode(), KEY_VNPAY_TMN, "VNPay TMN"),
				requireVnpayHashSecret(), url);
	}

	@Transactional(readOnly = true)
	public ZaloPayRuntimeConfig loadZaloPayForCreate() {
		assertZaloPayEnabled();
		PaymentGatewayProperties.Zalopay zp = paymentGatewayProperties.getZalopay();
		return new ZaloPayRuntimeConfig(
				requireYamlOrDb(zp.getAppId(), KEY_ZALO_APP_ID, "ZaloPay App ID"),
				requireYamlOrDb(zp.getKey1(), KEY_ZALO_KEY1, "ZaloPay key1"),
				requireYamlOrDb(zp.getKey2(), KEY_ZALO_KEY2, "ZaloPay key2"),
				requireYamlOrDb(zp.getEndpoint(), KEY_ZALO_ENDPOINT, "ZaloPay endpoint"),
				requireYamlOrDb(zp.getCallbackUrl(), KEY_ZALO_CALLBACK_URL, "ZaloPay callback URL"));
	}

	@Transactional(readOnly = true)
	public ZaloPayRuntimeConfig loadZaloPayForCallback() {
		PaymentGatewayProperties.Zalopay zp = paymentGatewayProperties.getZalopay();
		return new ZaloPayRuntimeConfig(
				requireYamlOrDb(zp.getAppId(), KEY_ZALO_APP_ID, "ZaloPay App ID"),
				requireYamlOrDb(zp.getKey1(), KEY_ZALO_KEY1, "ZaloPay key1"),
				requireYamlOrDb(zp.getKey2(), KEY_ZALO_KEY2, "ZaloPay key2"),
				getOptional(KEY_ZALO_ENDPOINT),
				getOptional(KEY_ZALO_CALLBACK_URL));
	}

	@Transactional(readOnly = true)
	public String frontendBaseUrl() {
		String y = nonBlankOrNull(paymentGatewayProperties.getFrontendBaseUrl());
		if (y != null) {
			return y;
		}
		return requireYamlOrDb("", KEY_APP_FRONTEND_BASE_URL, "URL frontend ứng dụng");
	}

	public record VnpayRuntimeConfig(String tmnCode, String hashSecret, String payUrl, String returnUrl,
			String ipnUrl) {
	}

	public record VnpayMerchantApiConfig(String tmnCode, String hashSecret, String merchantApiUrl) {
	}

	public record ZaloPayRuntimeConfig(String appId, String key1, String key2, String endpoint, String callbackUrl) {
	}
}
