package scu.dn.used_cars_backend.service.payment;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
	void hmac256_zaloMacLine() {
		String key1 = "key1demo";
		String line = "2553|250403_1_abcd|42|50000|1712123456789|{}|[]";
		String mac = PaymentHmacUtil.hmacSha256Hex(key1, line);
		String again = PaymentHmacUtil.hmacSha256Hex(key1, line);
		assertEquals(mac, again);
	}

	@Test
	void vnpay_verify_roundTrip() {
		VnpayService vnpayService = new VnpayService();
		String secret = "test-hash-secret-32chars-min";
		TreeMap<String, String> fields = new TreeMap<>();
		fields.put("vnp_Amount", "1000000");
		fields.put("vnp_Command", "pay");
		fields.put("vnp_CurrCode", "VND");
		fields.put("vnp_TmnCode", "TESTTMN");
		fields.put("vnp_TxnRef", "U1Tabc");
		String hashData = fields.entrySet().stream()
				.map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
		String hash = PaymentHmacUtil.hmacSha512Hex(secret, hashData);
		fields.put("vnp_SecureHash", hash);
		assertTrue(vnpayService.verifySignature(Map.copyOf(fields), secret));
	}
}
