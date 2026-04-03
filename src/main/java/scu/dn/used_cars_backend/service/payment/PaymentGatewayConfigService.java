package scu.dn.used_cars_backend.service.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.repository.SystemConfigRepository;

@Service
@RequiredArgsConstructor
public class PaymentGatewayConfigService {

	public static final String KEY_VNPAY_TMN = "vnpay_tmn_code";
	public static final String KEY_VNPAY_HASH_SECRET = "vnpay_hash_secret";
	public static final String KEY_VNPAY_PAY_URL = "vnpay_pay_url";
	public static final String KEY_VNPAY_RETURN_URL = "vnpay_return_url";
	public static final String KEY_VNPAY_IPN_URL = "vnpay_ipn_url";
	public static final String KEY_VNPAY_ENABLED = "vnpay_enabled";
	public static final String KEY_VNPAY_MERCHANT_API_URL = "vnpay_merchant_api_url";

	public static final String DEFAULT_VNPAY_MERCHANT_API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";

	public static final String KEY_ZALO_APP_ID = "zalopay_app_id";
	public static final String KEY_ZALO_KEY1 = "zalopay_key1";
	public static final String KEY_ZALO_KEY2 = "zalopay_key2";
	public static final String KEY_ZALO_ENDPOINT = "zalopay_endpoint";
	public static final String KEY_ZALO_CALLBACK_URL = "zalopay_callback_url";
	public static final String KEY_ZALO_ENABLED = "zalopay_enabled";

	public static final String KEY_APP_FRONTEND_BASE_URL = "app_frontend_base_url";

	private final SystemConfigRepository systemConfigRepository;

	@Transactional(readOnly = true)
	public String requireNonBlank(String key) {
		return systemConfigRepository.findByConfigKey(key)
				.map(r -> r.getConfigValue() != null ? r.getConfigValue().trim() : "")
				.filter(s -> !s.isEmpty())
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Thiếu cấu hình: " + key));
	}

	@Transactional(readOnly = true)
	public String getOptional(String key) {
		return systemConfigRepository.findByConfigKey(key)
				.map(r -> r.getConfigValue() != null ? r.getConfigValue().trim() : "")
				.orElse("");
	}

	@Transactional(readOnly = true)
	public boolean isTruthy(String key) {
		String v = getOptional(key);
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

	@Transactional(readOnly = true)
	public VnpayRuntimeConfig loadVnpayForCreate() {
		assertVnpayEnabled();
		return new VnpayRuntimeConfig(
				requireNonBlank(KEY_VNPAY_TMN),
				requireNonBlank(KEY_VNPAY_HASH_SECRET),
				requireNonBlank(KEY_VNPAY_PAY_URL),
				requireNonBlank(KEY_VNPAY_RETURN_URL),
				requireNonBlank(KEY_VNPAY_IPN_URL));
	}

	@Transactional(readOnly = true)
	public VnpayRuntimeConfig loadVnpayForVerify() {
		return new VnpayRuntimeConfig(
				requireNonBlank(KEY_VNPAY_TMN),
				requireNonBlank(KEY_VNPAY_HASH_SECRET),
				getOptional(KEY_VNPAY_PAY_URL),
				getOptional(KEY_VNPAY_RETURN_URL),
				getOptional(KEY_VNPAY_IPN_URL));
	}

	@Transactional(readOnly = true)
	public VnpayMerchantApiConfig loadVnpayForMerchantApi() {
		assertVnpayEnabled();
		String url = getOptional(KEY_VNPAY_MERCHANT_API_URL);
		if (url.isBlank()) {
			url = DEFAULT_VNPAY_MERCHANT_API_URL;
		}
		return new VnpayMerchantApiConfig(requireNonBlank(KEY_VNPAY_TMN), requireNonBlank(KEY_VNPAY_HASH_SECRET), url);
	}

	@Transactional(readOnly = true)
	public ZaloPayRuntimeConfig loadZaloPayForCreate() {
		assertZaloPayEnabled();
		return new ZaloPayRuntimeConfig(
				requireNonBlank(KEY_ZALO_APP_ID),
				requireNonBlank(KEY_ZALO_KEY1),
				requireNonBlank(KEY_ZALO_KEY2),
				requireNonBlank(KEY_ZALO_ENDPOINT),
				requireNonBlank(KEY_ZALO_CALLBACK_URL));
	}

	@Transactional(readOnly = true)
	public ZaloPayRuntimeConfig loadZaloPayForCallback() {
		return new ZaloPayRuntimeConfig(
				requireNonBlank(KEY_ZALO_APP_ID),
				requireNonBlank(KEY_ZALO_KEY1),
				requireNonBlank(KEY_ZALO_KEY2),
				getOptional(KEY_ZALO_ENDPOINT),
				getOptional(KEY_ZALO_CALLBACK_URL));
	}

	@Transactional(readOnly = true)
	public String frontendBaseUrl() {
		return requireNonBlank(KEY_APP_FRONTEND_BASE_URL);
	}

	public record VnpayRuntimeConfig(String tmnCode, String hashSecret, String payUrl, String returnUrl,
			String ipnUrl) {
	}

	public record VnpayMerchantApiConfig(String tmnCode, String hashSecret, String merchantApiUrl) {
	}

	public record ZaloPayRuntimeConfig(String appId, String key1, String key2, String endpoint, String callbackUrl) {
	}
}
