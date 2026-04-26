package scu.dn.used_cars_backend.dto.payment;

import java.math.BigDecimal;

public record OrderPaymentStaffRowDto(long id, String paymentMethod, String status, BigDecimal amount,
		String transactionRef, String vnpPayCreateDate, String vnpGatewayTransactionNo,
		String vnpLastRefundRequestId) {
}
