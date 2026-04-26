package scu.dn.used_cars_backend.service.payment;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentGatewaySigningTest {

	@Test
	void hmac512_sameInput_sameHex() {
		String a = PaymentHmacUtil.hmacSha512Hex("s3cr3t", "payload");
		String b = PaymentHmacUtil.hmacSha512Hex("s3cr3t", "payload");
		assertEquals(a, b);
	}

	@Test
	void hmac512_producesLowercaseHex() {
		String hex = PaymentHmacUtil.hmacSha512Hex("secret", "data");
		assertEquals(hex, hex.toLowerCase(), "HMAC output must be lowercase hex");
	}

	@Test
	void vnpay_hashSecret_normalizeStripsBom() {
		assertEquals("abc", PaymentGatewayConfigService.normalizeVnpHashSecret("\uFEFFabc"));
		assertEquals("abc", PaymentGatewayConfigService.normalizeVnpHashSecret(" abc "));
	}

	@Test
	void hmac256_zaloMacLine() {
		String key1 = "key1demo";
		String line = "2553|250403_1_abcd|42|50000|1712123456789|{}|[]";
		String mac = PaymentHmacUtil.hmacSha256Hex(key1, line);
		String again = PaymentHmacUtil.hmacSha256Hex(key1, line);
		assertEquals(mac, again);
	}

	@Test
	void vnpay_verify_roundTrip_withSpecialChars() {
		PaymentGatewayConfigService gate = mock(PaymentGatewayConfigService.class);
		when(gate.getOptional(anyString())).thenReturn("");
		VnpayService vnpayService = new VnpayService(gate);
		String secret = "test-hash-secret-32chars-minimum-length";

		TreeMap<String, String> fields = new TreeMap<>();
		fields.put("vnp_Amount", "1000000");
		fields.put("vnp_Command", "pay");
		fields.put("vnp_CurrCode", "VND");
		fields.put("vnp_OrderInfo", "Dat coc id 123");
		fields.put("vnp_ReturnUrl", "https://example.com/api/v1/payment/vnpay/return");
		fields.put("vnp_TmnCode", "TESTTMN");
		fields.put("vnp_TxnRef", "D1Tabc123");

		List<String> fieldNames = new ArrayList<>(fields.keySet());
		Collections.sort(fieldNames);
		List<String> validNames = new ArrayList<>();
		for (String name : fieldNames) {
			String v = fields.get(name);
			if (v != null && v.length() > 0) {
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

		String hash = PaymentHmacUtil.hmacSha512Hex(secret, hashData.toString());
		fields.put("vnp_SecureHash", hash);
		assertTrue(vnpayService.verifySignature(Map.copyOf(fields), secret));
	}

	@Test
	void vnpay_verify_acceptsSha256() {
		PaymentGatewayConfigService gate = mock(PaymentGatewayConfigService.class);
		when(gate.getOptional(anyString())).thenReturn("");
		VnpayService vnpayService = new VnpayService(gate);
		String secret = "test-hash-secret-32chars-minimum-length";

		TreeMap<String, String> fields = new TreeMap<>();
		fields.put("vnp_Amount", "1000000");
		fields.put("vnp_Command", "pay");
		fields.put("vnp_CurrCode", "VND");
		fields.put("vnp_OrderInfo", "Dat coc id 123");
		fields.put("vnp_ReturnUrl", "https://example.com/api/v1/payment/vnpay/return");
		fields.put("vnp_TmnCode", "TESTTMN");
		fields.put("vnp_TxnRef", "D1Tabc123");

		List<String> fieldNames = new ArrayList<>(fields.keySet());
		Collections.sort(fieldNames);
		List<String> validNames = new ArrayList<>();
		for (String name : fieldNames) {
			String v = fields.get(name);
			if (v != null && v.length() > 0) {
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

		String hash = PaymentHmacUtil.hmacSha256Hex(secret, hashData.toString());
		fields.put("vnp_SecureHash", hash);
		assertTrue(vnpayService.verifySignature(Map.copyOf(fields), secret));
	}

	@Test
	void vnpay_formEncoding_spaceBecomesPlus() {
		String encoded = VnpayFormEncoding.encodeValue("Dat coc id 123");
		assertTrue(encoded.contains("+"));
		assertEquals("Dat+coc+id+123", encoded);
	}
}
