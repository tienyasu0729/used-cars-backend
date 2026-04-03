package scu.dn.used_cars_backend.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.payment.OrderPaymentStaffRowDto;
import scu.dn.used_cars_backend.dto.payment.PaymentCreateRequest;
import scu.dn.used_cars_backend.dto.payment.PaymentUrlResponse;
import scu.dn.used_cars_backend.entity.OrderPayment;
import scu.dn.used_cars_backend.entity.SalesOrder;
import scu.dn.used_cars_backend.repository.OrderPaymentRepository;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;
import scu.dn.used_cars_backend.service.StaffService;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

	private final SalesOrderRepository salesOrderRepository;
	private final OrderPaymentRepository orderPaymentRepository;
	private final PaymentGatewayConfigService paymentGatewayConfigService;
	private final VnpayService vnpayService;
	private final VnpayMerchantApiService vnpayMerchantApiService;
	private final ZaloPayService zaloPayService;
	private final ObjectMapper objectMapper;
	private final StaffService staffService;

	@Transactional
	public PaymentUrlResponse createVnpay(long userId, PaymentCreateRequest req, String clientIp) {
		SalesOrder order = loadOrderAndAssertOwner(req.getOrderId(), userId);
		assertPayableAmount(order, req.getAmount());
		var cfg = paymentGatewayConfigService.loadVnpayForCreate();
		String txnRef = "U" + order.getId() + "T" + Long.toHexString(System.nanoTime());
		OrderPayment pay = new OrderPayment();
		pay.setOrder(order);
		pay.setAmount(req.getAmount());
		pay.setPaymentMethod("vnpay");
		pay.setTransactionRef(txnRef);
		pay.setStatus("Pending");
		String info = "Thanh toan don " + order.getOrderNumber();
		VnpayService.VnpayPayUrlResult built = vnpayService.buildPaymentUrl(cfg, txnRef, req.getAmount(), info,
				clientIp);
		pay.setVnpPayCreateDate(built.vnpCreateDate());
		orderPaymentRepository.save(pay);
		return new PaymentUrlResponse(built.paymentUrl());
	}

	@Transactional
	public PaymentUrlResponse createZaloPay(long userId, PaymentCreateRequest req) {
		SalesOrder order = loadOrderAndAssertOwner(req.getOrderId(), userId);
		assertPayableAmount(order, req.getAmount());
		var cfg = paymentGatewayConfigService.loadZaloPayForCreate();
		String appTransId = LocalDate.now(VN).format(YYMMDD) + "_" + order.getId() + "_"
				+ Long.toHexString(System.nanoTime());
		String transId = appTransId.length() <= 40 ? appTransId : appTransId.substring(0, 40);
		OrderPayment pay = new OrderPayment();
		pay.setOrder(order);
		pay.setAmount(req.getAmount());
		pay.setPaymentMethod("zalopay");
		pay.setTransactionRef(transId);
		pay.setStatus("Pending");
		orderPaymentRepository.save(pay);
		String orderUrl = zaloPayService.createOrderAndGetPayUrl(cfg, transId, req.getAmount().longValueExact(),
				String.valueOf(userId), "Thanh toan don " + order.getOrderNumber());
		if (orderUrl == null || orderUrl.isBlank()) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "ZaloPay thieu order_url.");
		}
		return new PaymentUrlResponse(orderUrl);
	}

	public Optional<String> tryCompleteVnpay(Map<String, String> params) {
		var cfg = paymentGatewayConfigService.loadVnpayForVerify();
		if (!vnpayService.verifySignature(params, cfg.hashSecret())) {
			return Optional.of("INVALID_SIGNATURE");
		}
		if (!cfg.tmnCode().equals(params.get("vnp_TmnCode"))) {
			return Optional.of("INVALID_TMNCODE");
		}
		String txnRef = params.get("vnp_TxnRef");
		if (txnRef == null || txnRef.isBlank()) {
			return Optional.of("MISSING_TXN");
		}
		OrderPayment p = orderPaymentRepository.findByTransactionRef(txnRef).orElse(null);
		if (p == null) {
			return Optional.of("NOT_FOUND");
		}
		if ("Completed".equals(p.getStatus())) {
			return Optional.empty();
		}
		String amountStr = params.get("vnp_Amount");
		if (amountStr == null) {
			return Optional.of("MISSING_AMOUNT");
		}
		BigDecimal paid = BigDecimal.valueOf(Long.parseLong(amountStr)).divide(BigDecimal.valueOf(100));
		if (paid.compareTo(p.getAmount()) != 0) {
			return Optional.of("AMOUNT_MISMATCH");
		}
		if (!"00".equals(params.get("vnp_TransactionStatus"))) {
			return Optional.of("NOT_SUCCESS");
		}
		if (!"00".equals(params.get("vnp_ResponseCode"))) {
			return Optional.of("RESP_" + params.get("vnp_ResponseCode"));
		}
		completePaymentAndOrder(p, "vnpay", params.get("vnp_TransactionNo"));
		return Optional.empty();
	}

	@Transactional(readOnly = true)
	public JsonNode staffQueryVnpay(long actorUserId, boolean isAdmin, long orderPaymentId, String serverIp) {
		OrderPayment p = orderPaymentRepository.findByIdWithOrderAndBranch(orderPaymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Khong tim thay thanh toan."));
		if (!"vnpay".equalsIgnoreCase(p.getPaymentMethod())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chi ho tro thanh toan VNPay.");
		}
		assertActorCanAccessOrder(actorUserId, isAdmin, p.getOrder());
		String payCreate = p.getVnpPayCreateDate();
		if (payCreate == null || payCreate.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Thieu vnp_pay_create_date — giao dich tao truoc khi cap nhat he thong.");
		}
		var cfg = paymentGatewayConfigService.loadVnpayForMerchantApi();
		String reqId = VnpayMerchantApiService.newRequestId();
		var raw = vnpayMerchantApiService.queryDr(cfg, reqId, p.getTransactionRef(), payCreate.trim(),
				null, p.getVnpGatewayTransactionNo(), serverIp);
		return vnpayMerchantApiService.stripSecureHash(raw);
	}

	@Transactional
	public JsonNode staffRefundVnpay(long actorUserId, boolean isAdmin, long orderPaymentId, String serverIp,
			String createBy, String orderInfo) {
		OrderPayment p = orderPaymentRepository.findByIdWithOrderAndBranch(orderPaymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Khong tim thay thanh toan."));
		if (!"vnpay".equalsIgnoreCase(p.getPaymentMethod())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chi ho tro thanh toan VNPay.");
		}
		assertActorCanAccessOrder(actorUserId, isAdmin, p.getOrder());
		if (!"Completed".equals(p.getStatus())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chi hoan tien khi thanh toan da Completed.");
		}
		if (p.getVnpLastRefundRequestId() != null && !p.getVnpLastRefundRequestId().isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Giao dich da co yeu cau hoan tien truoc do.");
		}
		String payCreate = p.getVnpPayCreateDate();
		if (payCreate == null || payCreate.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Thieu vnp_pay_create_date — khong the hoan tien.");
		}
		long amountMinor = p.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValueExact();
		var cfg = paymentGatewayConfigService.loadVnpayForMerchantApi();
		String reqId = VnpayMerchantApiService.newRequestId();
		var resp = vnpayMerchantApiService.refund(cfg, reqId, p.getTransactionRef(), amountMinor, "02",
				payCreate.trim(), p.getVnpGatewayTransactionNo(), createBy, orderInfo, serverIp);
		String rc = resp.path("vnp_ResponseCode").asText("");
		if ("00".equals(rc)) {
			p.setStatus("Refunded");
			p.setVnpLastRefundRequestId(reqId);
			orderPaymentRepository.save(p);
		}
		else if ("94".equals(rc)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"VNPay: da gui yeu cau hoan tien truoc do (94).");
		}
		else {
			String msg = resp.path("vnp_Message").asText(rc);
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "VNPay: " + msg);
		}
		return vnpayMerchantApiService.stripSecureHash(resp);
	}

	@Transactional(readOnly = true)
	public List<OrderPaymentStaffRowDto> listPaymentsForStaffOrder(long actorUserId, boolean isAdmin, long orderId) {
		SalesOrder order = salesOrderRepository.findByIdWithBranch(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Khong tim thay don."));
		assertActorCanAccessOrder(actorUserId, isAdmin, order);
		return orderPaymentRepository.findByOrderIdWithOrderAndBranch(orderId).stream().map(this::toStaffPaymentRow)
				.toList();
	}

	private OrderPaymentStaffRowDto toStaffPaymentRow(OrderPayment p) {
		return new OrderPaymentStaffRowDto(p.getId(), p.getPaymentMethod(), p.getStatus(), p.getAmount(),
				p.getTransactionRef(), p.getVnpPayCreateDate(), p.getVnpGatewayTransactionNo(),
				p.getVnpLastRefundRequestId());
	}

	private void assertActorCanAccessOrder(long actorUserId, boolean isAdmin, SalesOrder order) {
		if (isAdmin) {
			return;
		}
		int bid = staffService.getManagerBranchId(actorUserId);
		if (order.getBranch().getId() != bid) {
			throw new BusinessException(ErrorCode.PAYMENT_FORBIDDEN, "Khong co quyen thao tac thanh toan nay.");
		}
	}

	@Transactional
	public void handleVnpayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
		Map<String, String> m = flattenParams(request);
		Optional<String> err = tryCompleteVnpay(m);
		String base = paymentGatewayConfigService.frontendBaseUrl().replaceAll("/$", "");
		Long orderId = Optional.ofNullable(m.get("vnp_TxnRef"))
				.flatMap(orderPaymentRepository::findByTransactionRef)
				.map(p -> p.getOrder().getId())
				.orElse(null);
		boolean ok = err.isEmpty();
		String code = ok ? "00" : err.orElse("ERROR");
		String q = "success=" + ok + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
				+ (orderId != null ? "&orderId=" + orderId : "");
		response.sendRedirect(base + "/payment/result?" + q);
	}

	@Transactional
	public Map<String, String> handleVnpayIpn(HttpServletRequest request) {
		Map<String, String> m = flattenParams(request);
		Optional<String> err = tryCompleteVnpay(m);
		if (err.isPresent()) {
			String c = err.get();
			if ("INVALID_SIGNATURE".equals(c)) {
				return Map.of("RspCode", "97", "Message", "Invalid Checksum");
			}
			if ("NOT_FOUND".equals(c)) {
				return Map.of("RspCode", "01", "Message", "Order not found");
			}
			if ("AMOUNT_MISMATCH".equals(c)) {
				return Map.of("RspCode", "04", "Message", "Invalid amount");
			}
			if ("NOT_SUCCESS".equals(c) || c.startsWith("RESP_")) {
				return Map.of("RspCode", "00", "Message", "Acknowledged");
			}
			return Map.of("RspCode", "99", "Message", "Unknown error");
		}
		return Map.of("RspCode", "00", "Message", "Confirm Success");
	}

	@Transactional
	public Map<String, Object> handleZaloCallback(String jsonBody) {
		try {
			JsonNode root = objectMapper.readTree(jsonBody);
			String data = root.path("data").asText("");
			String mac = root.path("mac").asText("");
			var cfg = paymentGatewayConfigService.loadZaloPayForCallback();
			if (!zaloPayService.verifyCallbackMac(data, mac, cfg.key2())) {
				return Map.of("return_code", -1, "return_message", "mac invalid");
			}
			JsonNode payload = zaloPayService.parseCallbackDataJson(data);
			String appTransId = payload.path("app_trans_id").asText("");
			if (appTransId.isBlank()) {
				return Map.of("return_code", -2, "return_message", "missing app_trans_id");
			}
			long amount = payload.path("amount").asLong(-1);
			OrderPayment p = orderPaymentRepository.findByTransactionRef(appTransId).orElse(null);
			if (p == null) {
				return Map.of("return_code", -3, "return_message", "order not found");
			}
			if ("Completed".equals(p.getStatus())) {
				return Map.of("return_code", 1, "return_message", "success");
			}
			if (amount < 0 || BigDecimal.valueOf(amount).compareTo(p.getAmount()) != 0) {
				return Map.of("return_code", -4, "return_message", "amount mismatch");
			}
			boolean paid = payload.hasNonNull("zp_trans_id") || payload.path("result").asInt(0) == 1;
			if (!paid) {
				return Map.of("return_code", 1, "return_message", "success");
			}
			completePaymentAndOrder(p, "zalopay", null);
			return Map.of("return_code", 1, "return_message", "success");
		}
		catch (Exception e) {
			return Map.of("return_code", -9, "return_message", "parse error");
		}
	}

	private void completePaymentAndOrder(OrderPayment p, String method, String vnpGatewayTransactionNo) {
		p.setStatus("Completed");
		p.setPaidAt(Instant.now());
		if (vnpGatewayTransactionNo != null && !vnpGatewayTransactionNo.isBlank()) {
			p.setVnpGatewayTransactionNo(vnpGatewayTransactionNo.trim());
		}
		SalesOrder o = p.getOrder();
		o.setStatus("Processing");
		o.setPaymentMethod(method);
		orderPaymentRepository.save(p);
		salesOrderRepository.save(o);
	}

	private SalesOrder loadOrderAndAssertOwner(long orderId, long userId) {
		SalesOrder o = salesOrderRepository.findById(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Khong tim thay don."));
		if (o.getCustomerId() == null || o.getCustomerId() != userId) {
			throw new BusinessException(ErrorCode.PAYMENT_FORBIDDEN, "Khong co quyen thanh toan don nay.");
		}
		if ("Cancelled".equals(o.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Don da huy.");
		}
		return o;
	}

	private static void assertPayableAmount(SalesOrder order, BigDecimal amount) {
		if (amount.compareTo(order.getDepositAmount()) == 0 || amount.compareTo(order.getRemainingAmount()) == 0) {
			return;
		}
		throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "So tien khong khop tien coc hoac con lai.");
	}

	private static Map<String, String> flattenParams(HttpServletRequest req) {
		Map<String, String> m = new TreeMap<>();
		req.getParameterMap().forEach((k, v) -> {
			if (v != null && v.length > 0) {
				m.put(k, v[0]);
			}
		});
		return m;
	}
}
