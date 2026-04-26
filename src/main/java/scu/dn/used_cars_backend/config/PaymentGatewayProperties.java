package scu.dn.used_cars_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public class PaymentGatewayProperties {

	private String frontendBaseUrl = "";
	private Vnpay vnpay = new Vnpay();
	private Zalopay zalopay = new Zalopay();

	public String getFrontendBaseUrl() {
		return frontendBaseUrl;
	}

	public void setFrontendBaseUrl(String frontendBaseUrl) {
		this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl;
	}

	public Vnpay getVnpay() {
		return vnpay;
	}

	public void setVnpay(Vnpay vnpay) {
		if (vnpay != null) {
			this.vnpay = vnpay;
		}
	}

	public Zalopay getZalopay() {
		return zalopay;
	}

	public void setZalopay(Zalopay zalopay) {
		if (zalopay != null) {
			this.zalopay = zalopay;
		}
	}

	public static class Vnpay {
		private String tmnCode = "";
		private String hashSecret = "";
		private String payUrl = "";
		private String returnUrl = "";
		private String ipnUrl = "";
		private String merchantApiUrl = "";
		private String customerIpFallback = "";
		private String orderType = "";
		private String hmacAlgorithm = "";

		public String getTmnCode() {
			return tmnCode;
		}

		public void setTmnCode(String tmnCode) {
			this.tmnCode = tmnCode == null ? "" : tmnCode;
		}

		public String getHashSecret() {
			return hashSecret;
		}

		public void setHashSecret(String hashSecret) {
			this.hashSecret = hashSecret == null ? "" : hashSecret;
		}

		public String getPayUrl() {
			return payUrl;
		}

		public void setPayUrl(String payUrl) {
			this.payUrl = payUrl == null ? "" : payUrl;
		}

		public String getReturnUrl() {
			return returnUrl;
		}

		public void setReturnUrl(String returnUrl) {
			this.returnUrl = returnUrl == null ? "" : returnUrl;
		}

		public String getIpnUrl() {
			return ipnUrl;
		}

		public void setIpnUrl(String ipnUrl) {
			this.ipnUrl = ipnUrl == null ? "" : ipnUrl;
		}

		public String getMerchantApiUrl() {
			return merchantApiUrl;
		}

		public void setMerchantApiUrl(String merchantApiUrl) {
			this.merchantApiUrl = merchantApiUrl == null ? "" : merchantApiUrl;
		}

		public String getCustomerIpFallback() {
			return customerIpFallback;
		}

		public void setCustomerIpFallback(String customerIpFallback) {
			this.customerIpFallback = customerIpFallback == null ? "" : customerIpFallback;
		}

		public String getOrderType() {
			return orderType;
		}

		public void setOrderType(String orderType) {
			this.orderType = orderType == null ? "" : orderType;
		}

		public String getHmacAlgorithm() {
			return hmacAlgorithm;
		}

		public void setHmacAlgorithm(String hmacAlgorithm) {
			this.hmacAlgorithm = hmacAlgorithm == null ? "" : hmacAlgorithm;
		}
	}

	public static class Zalopay {
		private String appId = "";
		private String key1 = "";
		private String key2 = "";
		private String endpoint = "";
		private String callbackUrl = "";

		public String getAppId() {
			return appId;
		}

		public void setAppId(String appId) {
			this.appId = appId == null ? "" : appId;
		}

		public String getKey1() {
			return key1;
		}

		public void setKey1(String key1) {
			this.key1 = key1 == null ? "" : key1;
		}

		public String getKey2() {
			return key2;
		}

		public void setKey2(String key2) {
			this.key2 = key2 == null ? "" : key2;
		}

		public String getEndpoint() {
			return endpoint;
		}

		public void setEndpoint(String endpoint) {
			this.endpoint = endpoint == null ? "" : endpoint;
		}

		public String getCallbackUrl() {
			return callbackUrl;
		}

		public void setCallbackUrl(String callbackUrl) {
			this.callbackUrl = callbackUrl == null ? "" : callbackUrl;
		}
	}
}
