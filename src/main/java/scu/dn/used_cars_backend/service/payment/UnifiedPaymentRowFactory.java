package scu.dn.used_cars_backend.service.payment;

import scu.dn.used_cars_backend.dto.payment.UnifiedPaymentListItemDto;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.OrderPayment;
import scu.dn.used_cars_backend.entity.SalesOrder;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

public final class UnifiedPaymentRowFactory {

	private UnifiedPaymentRowFactory() {
	}

	public static UnifiedPaymentListItemDto fromOrderPayment(OrderPayment p, Map<Long, User> usersById) {
		SalesOrder o = p.getOrder();
		Vehicle v = o.getVehicle();
		User cust = usersById.get(o.getCustomerId());
		User staff = o.getStaffId() != null ? usersById.get(o.getStaffId()) : null;
		String method = norm(p.getPaymentMethod());
		String ref = firstNonBlank(p.getTransactionRef(), p.getVnpGatewayTransactionNo());
		if (ref == null) {
			ref = p.getId() != null ? "OP-" + p.getId() : "—";
		}
		GatewayLabel gw = gatewayOrderPayment(p, method);
		Instant upd = p.getPaidAt() != null ? p.getPaidAt() : p.getCreatedAt();
		return UnifiedPaymentListItemDto.builder()
				.unifiedId("order_payment:" + p.getId())
				.kind("ORDER_PAYMENT")
				.txnRefDisplay(ref)
				.customerId(o.getCustomerId())
				.customerName(cust != null ? cust.getName() : "—")
				.customerPhone(cust != null && cust.getPhone() != null ? cust.getPhone() : "—")
				.vehicleId(v.getId())
				.vehicleTitle(v.getTitle() != null ? v.getTitle() : "—")
				.listingId(v.getListingId())
				.amount(p.getAmount().toPlainString())
				.paymentMethod(method)
				.paymentMethodLabel(methodLabel(method))
				.businessStatus(p.getStatus())
				.gatewayStatusLabel(gw.labelVi())
				.gatewayStatusCode(gw.code())
				.createdAt(p.getCreatedAt().toString())
				.updatedAt(upd.toString())
				.branchId(o.getBranch().getId())
				.branchName(o.getBranch().getName())
				.staffUserId(o.getStaffId())
				.staffName(staff != null ? staff.getName() : null)
				.orderId(o.getId())
				.depositId(null)
				.orderPaymentId(p.getId())
				.build();
	}

	public static UnifiedPaymentListItemDto fromDeposit(Deposit d, Map<Long, User> usersById,
			Map<Long, Vehicle> vehiclesById) {
		Vehicle v = vehiclesById.get(d.getVehicleId());
		User cust = usersById.get(d.getCustomerId());
		User creator = d.getCreatedBy() != null ? usersById.get(d.getCreatedBy()) : null;
		String method = norm(d.getPaymentMethod());
		String gw = norm(d.getPaymentGateway());
		String ref = firstNonBlank(d.getGatewayTxnRef(), d.getGatewayTransRef());
		if (ref == null) {
			ref = "DEP-" + d.getId();
		}
		GatewayLabel gl = gatewayDeposit(d, method, gw);
		String branchName = "—";
		Integer branchId = null;
		if (v != null && v.getBranch() != null) {
			branchId = v.getBranch().getId();
			branchName = v.getBranch().getName();
		}
		String title = v != null && v.getTitle() != null ? v.getTitle() : "—";
		String listing = v != null ? v.getListingId() : "—";
		Long vid = v != null ? v.getId() : d.getVehicleId();
		return UnifiedPaymentListItemDto.builder()
				.unifiedId("deposit:" + d.getId())
				.kind("DEPOSIT")
				.txnRefDisplay(ref)
				.customerId(d.getCustomerId())
				.customerName(cust != null ? cust.getName() : "—")
				.customerPhone(cust != null && cust.getPhone() != null ? cust.getPhone() : "—")
				.vehicleId(vid)
				.vehicleTitle(title)
				.listingId(listing)
				.amount(d.getAmount().toPlainString())
				.paymentMethod(method)
				.paymentMethodLabel(methodLabel(method))
				.businessStatus(d.getStatus())
				.gatewayStatusLabel(gl.labelVi())
				.gatewayStatusCode(gl.code())
				.createdAt(d.getCreatedAt().toString())
				.updatedAt(d.getCreatedAt().toString())
				.branchId(branchId)
				.branchName(branchName)
				.staffUserId(d.getCreatedBy())
				.staffName(creator != null ? creator.getName() : null)
				.orderId(d.getOrderId())
				.depositId(d.getId())
				.orderPaymentId(null)
				.build();
	}

	private record GatewayLabel(String code, String labelVi) {
	}

	private static GatewayLabel gatewayOrderPayment(OrderPayment p, String method) {
		String st = p.getStatus();
		if ("Completed".equals(st)) {
			return new GatewayLabel("SUCCESS", "Thành công");
		}
		if ("Failed".equals(st) || "Refunded".equals(st)) {
			return new GatewayLabel("FAILED", "Thất bại");
		}
		if ("Pending".equals(st) && (method.contains("vnpay") || method.contains("zalo"))) {
			return new GatewayLabel("PROCESSING", "Đang xử lý");
		}
		if ("Pending".equals(st)) {
			return new GatewayLabel("NOT_STARTED", "Chưa thực hiện");
		}
		return new GatewayLabel("UNKNOWN", st != null ? st : "—");
	}

	private static GatewayLabel gatewayDeposit(Deposit d, String method, String gateway) {
		String st = d.getStatus();
		if ("Confirmed".equals(st) || "ConvertedToOrder".equals(st) || "Converted".equals(st)) {
			return new GatewayLabel("SUCCESS", "Thành công");
		}
		if ("Cancelled".equals(st) || "Expired".equals(st)) {
			return new GatewayLabel("FAILED", "Thất bại");
		}
		if ("AwaitingPayment".equals(st) && (gateway.contains("vnpay") || gateway.contains("zalo"))) {
			return new GatewayLabel("PROCESSING", "Đang xử lý");
		}
		if ("AwaitingPayment".equals(st)) {
			return new GatewayLabel("NOT_STARTED", "Chưa thực hiện");
		}
		if ("Pending".equals(st) && (method.contains("vnpay") || method.contains("zalo"))) {
			return new GatewayLabel("PROCESSING", "Đang xử lý");
		}
		if ("Pending".equals(st)) {
			return new GatewayLabel("NOT_STARTED", "Chưa thực hiện");
		}
		return new GatewayLabel("UNKNOWN", st != null ? st : "—");
	}

	private static String norm(String s) {
		return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
	}

	private static String firstNonBlank(String a, String b) {
		if (a != null && !a.isBlank()) {
			return a.trim();
		}
		if (b != null && !b.isBlank()) {
			return b.trim();
		}
		return null;
	}

	static String methodLabel(String methodNorm) {
		if (methodNorm.contains("vnpay")) {
			return "VNPay";
		}
		if (methodNorm.contains("zalo")) {
			return "ZaloPay";
		}
		return "Tiền mặt";
	}
}
