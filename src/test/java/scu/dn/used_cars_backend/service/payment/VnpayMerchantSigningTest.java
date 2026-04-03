package scu.dn.used_cars_backend.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import scu.dn.used_cars_backend.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VnpayMerchantSigningTest {

	@Test
	void querydr_request_data_matches_pipe_order() {
		assertEquals(
				"Q1|2.1.0|querydr|TMN01|REF1|20240101120000|20240101120001|127.0.0.1|Mo ta",
				VnpayMerchantSigning.queryDrRequestData("Q1", "2.1.0", "querydr", "TMN01", "REF1", "20240101120000",
						"20240101120001", "127.0.0.1", "Mo ta"));
	}

	@Test
	void refund_request_data_allows_empty_gateway_transaction_no() {
		assertEquals(
				"R1|2.1.0|refund|TMN01|02|REF1|500000||20240101120000|admin|20240101120005|10.0.0.1|Hoan tien",
				VnpayMerchantSigning.refundRequestData("R1", "2.1.0", "refund", "TMN01", "02", "REF1", "500000", "",
						"20240101120000", "admin", "20240101120005", "10.0.0.1", "Hoan tien"));
	}

	@Test
	void querydr_response_hash_roundtrip() {
		ObjectMapper om = new ObjectMapper();
		ObjectNode n = om.createObjectNode();
		n.put("vnp_ResponseId", "resp1");
		n.put("vnp_Command", "querydr");
		n.put("vnp_ResponseCode", "00");
		n.put("vnp_Message", "OK");
		n.put("vnp_TmnCode", "TMN");
		n.put("vnp_TxnRef", "T1");
		n.put("vnp_Amount", "100");
		n.put("vnp_BankCode", "NCB");
		n.put("vnp_PayDate", "20240101120000");
		n.put("vnp_TransactionNo", "123");
		n.put("vnp_TransactionType", "01");
		n.put("vnp_TransactionStatus", "00");
		n.put("vnp_OrderInfo", "info");
		n.put("vnp_PromotionCode", "");
		n.put("vnp_PromotionAmount", "");
		String secret = "unit-test-secret-key-min-length-ok!!";
		String sig = PaymentHmacUtil.hmacSha512Hex(secret, VnpayMerchantSigning.queryDrResponseData(n));
		n.put("vnp_SecureHash", sig);
		assertDoesNotThrow(() -> VnpayMerchantSigning.assertQueryDrResponseHash(n, secret));
	}

	@Test
	void refund_response_hash_roundtrip() {
		ObjectMapper om = new ObjectMapper();
		ObjectNode n = om.createObjectNode();
		n.put("vnp_ResponseId", "resp2");
		n.put("vnp_Command", "refund");
		n.put("vnp_ResponseCode", "00");
		n.put("vnp_Message", "OK");
		n.put("vnp_TmnCode", "TMN");
		n.put("vnp_TxnRef", "T1");
		n.put("vnp_Amount", "100");
		n.put("vnp_BankCode", "NCB");
		n.put("vnp_PayDate", "20240101120000");
		n.put("vnp_TransactionNo", "999");
		n.put("vnp_TransactionType", "02");
		n.put("vnp_TransactionStatus", "00");
		n.put("vnp_OrderInfo", "ref");
		String secret = "unit-test-secret-key-min-length-ok!!";
		String sig = PaymentHmacUtil.hmacSha512Hex(secret, VnpayMerchantSigning.refundResponseData(n));
		n.put("vnp_SecureHash", sig);
		assertDoesNotThrow(() -> VnpayMerchantSigning.assertRefundResponseHash(n, secret));
	}

	@Test
	void querydr_response_rejects_bad_hash() {
		ObjectMapper om = new ObjectMapper();
		ObjectNode n = om.createObjectNode();
		n.put("vnp_ResponseId", "x");
		n.put("vnp_SecureHash", "deadbeef");
		assertThrows(BusinessException.class, () -> VnpayMerchantSigning.assertQueryDrResponseHash(n, "secret"));
	}
}
