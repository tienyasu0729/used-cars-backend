package scu.dn.used_cars_backend.service.payment;

import com.fasterxml.jackson.databind.JsonNode;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;

public final class VnpayMerchantSigning {

	private VnpayMerchantSigning() {
	}

	public static String queryDrRequestData(String requestId, String version, String command, String tmnCode,
			String txnRef, String transactionDate, String createDate, String ipAddr, String orderInfo) {
		return requestId + "|" + version + "|" + command + "|" + tmnCode + "|" + txnRef + "|" + transactionDate + "|"
				+ createDate + "|" + ipAddr + "|" + orderInfo;
	}

	public static String refundRequestData(String requestId, String version, String command, String tmnCode,
			String transactionType, String txnRef, String amount, String transactionNo, String transactionDate,
			String createBy, String createDate, String ipAddr, String orderInfo) {
		return requestId + "|" + version + "|" + command + "|" + tmnCode + "|" + transactionType + "|" + txnRef + "|"
				+ amount + "|" + transactionNo + "|" + transactionDate + "|" + createBy + "|" + createDate + "|"
				+ ipAddr + "|" + orderInfo;
	}

	public static String queryDrResponseData(JsonNode r) {
		return jt(r, "vnp_ResponseId") + "|" + jt(r, "vnp_Command") + "|" + jt(r, "vnp_ResponseCode") + "|"
				+ jt(r, "vnp_Message") + "|" + jt(r, "vnp_TmnCode") + "|" + jt(r, "vnp_TxnRef") + "|"
				+ jt(r, "vnp_Amount") + "|" + jt(r, "vnp_BankCode") + "|" + jt(r, "vnp_PayDate") + "|"
				+ jt(r, "vnp_TransactionNo") + "|" + jt(r, "vnp_TransactionType") + "|" + jt(r, "vnp_TransactionStatus")
				+ "|" + jt(r, "vnp_OrderInfo") + "|" + jt(r, "vnp_PromotionCode") + "|" + jt(r, "vnp_PromotionAmount");
	}

	public static String refundResponseData(JsonNode r) {
		return jt(r, "vnp_ResponseId") + "|" + jt(r, "vnp_Command") + "|" + jt(r, "vnp_ResponseCode") + "|"
				+ jt(r, "vnp_Message") + "|" + jt(r, "vnp_TmnCode") + "|" + jt(r, "vnp_TxnRef") + "|"
				+ jt(r, "vnp_Amount") + "|" + jt(r, "vnp_BankCode") + "|" + jt(r, "vnp_PayDate") + "|"
				+ jt(r, "vnp_TransactionNo") + "|" + jt(r, "vnp_TransactionType") + "|" + jt(r, "vnp_TransactionStatus")
				+ "|" + jt(r, "vnp_OrderInfo");
	}

	public static void assertQueryDrResponseHash(JsonNode r, String secret) {
		String incoming = jt(r, "vnp_SecureHash");
		if (incoming.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "VNPay querydr thieu vnp_SecureHash.");
		}
		String computed = PaymentHmacUtil.hmacSha512Hex(secret, queryDrResponseData(r));
		if (!incoming.equalsIgnoreCase(computed)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "VNPay querydr checksum khong hop le.");
		}
	}

	public static void assertRefundResponseHash(JsonNode r, String secret) {
		String incoming = jt(r, "vnp_SecureHash");
		if (incoming.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "VNPay refund thieu vnp_SecureHash.");
		}
		String computed = PaymentHmacUtil.hmacSha512Hex(secret, refundResponseData(r));
		if (!incoming.equalsIgnoreCase(computed)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "VNPay refund checksum khong hop le.");
		}
	}

	static String jt(JsonNode n, String k) {
		JsonNode x = n.get(k);
		return x == null || x.isNull() ? "" : x.asText("");
	}
}
