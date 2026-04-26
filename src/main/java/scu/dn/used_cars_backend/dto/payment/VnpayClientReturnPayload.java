package scu.dn.used_cars_backend.dto.payment;

public record VnpayClientReturnPayload(boolean success, String code, String kind, Long orderId, Long depositId) {
}
