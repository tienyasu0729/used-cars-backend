package scu.dn.used_cars_backend.service.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.payment.OrderPaymentStaffRowDto;
import scu.dn.used_cars_backend.dto.payment.PaymentCreateRequest;
import scu.dn.used_cars_backend.dto.payment.PaymentUrlResponse;
import scu.dn.used_cars_backend.dto.payment.VnpayClientReturnPayload;
import scu.dn.used_cars_backend.dto.payment.ZaloPayStatusResponse;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.FinancialTransaction;
import scu.dn.used_cars_backend.entity.OrderPayment;
import scu.dn.used_cars_backend.entity.SalesOrder;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleStatus;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.FinancialTransactionRepository;
import scu.dn.used_cars_backend.repository.OrderPaymentRepository;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.service.DepositService;
import scu.dn.used_cars_backend.service.StaffService;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

	private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);
	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

	private final SalesOrderRepository salesOrderRepository;
	private final OrderPaymentRepository orderPaymentRepository;
	private final DepositRepository depositRepository;
	private final FinancialTransactionRepository financialTransactionRepository;
	private final PaymentGatewayConfigService paymentGatewayConfigService;
	private final VnpayService vnpayService;
	private final VnpayMerchantApiService vnpayMerchantApiService;
	private final ZaloPayService zaloPayService;
	private final ObjectMapper objectMapper;
	private final StaffService staffService;
	private final DepositService depositService;
	private final VehicleRepository vehicleRepository;

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
		String info = "Thanh toan don hang id " + order.getId();
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
		String base = paymentGatewayConfigService.frontendBaseUrl().replaceAll("/$", "");
		String redirect = base + "/payment/result?kind=zalo_order&orderId=" + order.getId();
		String embed;
		try {
			embed = objectMapper.writeValueAsString(Map.of("redirecturl", redirect));
		}
		catch (JsonProcessingException e) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo embed_data ZaloPay.");
		}
		String orderUrl = zaloPayService.createOrderAndGetPayUrl(cfg, transId, req.getAmount().longValueExact(),
				String.valueOf(userId), "Thanh toan don " + order.getOrderNumber(), embed);
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
		if (p != null) {
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
		Deposit d = depositRepository.findByGatewayTxnRef(txnRef).orElse(null);
		if (d == null) {
			return Optional.of("NOT_FOUND");
		}
		// Xu ly ca Pending va AwaitingPayment
		if (!"Pending".equals(d.getStatus()) && !"AwaitingPayment".equals(d.getStatus())) {
			return Optional.empty();
		}
		if (!"vnpay".equalsIgnoreCase(d.getPaymentMethod())) {
			return Optional.of("METHOD_MISMATCH");
		}
		String amountStrDep = params.get("vnp_Amount");
		if (amountStrDep == null) {
			return Optional.of("MISSING_AMOUNT");
		}
		BigDecimal paidDep = BigDecimal.valueOf(Long.parseLong(amountStrDep)).divide(BigDecimal.valueOf(100));
		if (paidDep.compareTo(d.getAmount()) != 0) {
			return Optional.of("AMOUNT_MISMATCH");
		}
		if (!"00".equals(params.get("vnp_TransactionStatus"))) {
			depositService.cancelPendingDepositAfterOnlinePaymentDeclined(d.getId());
			return Optional.of("NOT_SUCCESS");
		}
		if (!"00".equals(params.get("vnp_ResponseCode"))) {
			depositService.cancelPendingDepositAfterOnlinePaymentDeclined(d.getId());
			return Optional.of("RESP_" + params.get("vnp_ResponseCode"));
		}
		completeDepositAfterOnlinePayment(d, params.get("vnp_TransactionNo"));
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

	@Transactional(readOnly = true)
	public List<OrderPaymentStaffRowDto> listPaymentsForCustomer(long customerUserId, long orderId) {
		SalesOrder order = salesOrderRepository.findByIdWithBranch(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Khong tim thay don."));
		if (order.getCustomerId() == null || order.getCustomerId() != customerUserId) {
			throw new BusinessException(ErrorCode.PAYMENT_FORBIDDEN, "Khong co quyen xem thanh toan don nay.");
		}
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
	public VnpayClientReturnPayload completeVnpayReturnAndBuildPayload(HttpServletRequest request) {
		Map<String, String> m = flattenParams(request);
		Optional<String> err = tryCompleteVnpay(m);
		String txn = m.get("vnp_TxnRef");
		Long orderId = Optional.ofNullable(txn)
				.flatMap(orderPaymentRepository::findByTransactionRef)
				.map(p -> p.getOrder().getId())
				.orElse(null);
		Long depositId = orderId != null ? null
				: Optional.ofNullable(txn).flatMap(depositRepository::findByGatewayTxnRef).map(Deposit::getId)
						.orElse(null);
		boolean ok = err.isEmpty();
		String code = ok ? "00" : err.orElse("ERROR");
		return new VnpayClientReturnPayload(ok, code, "vnpay", orderId, depositId);
	}

	public URI buildVnpayFrontendResultUri(VnpayClientReturnPayload p) {
		String base = paymentGatewayConfigService.frontendBaseUrl().replaceAll("/$", "");
		String q = "success=" + p.success() + "&code=" + URLEncoder.encode(p.code(), StandardCharsets.UTF_8)
				+ "&kind=vnpay"
				+ (p.orderId() != null ? "&orderId=" + p.orderId() : "")
				+ (p.depositId() != null ? "&depositId=" + p.depositId() : "");
		return URI.create(base + "/payment/result?" + q);
	}

	@Transactional
	public ZaloPayStatusResponse customerQueryZaloPayStatus(long userId, Long orderId, Long depositId) {
		if ((orderId == null) == (depositId == null)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Truyền đúng một trong orderId hoặc depositId.");
		}
		var cfg = paymentGatewayConfigService.loadZaloPayForCreate();
		if (orderId != null) {
			SalesOrder o = salesOrderRepository.findById(orderId)
					.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Khong tim thay don."));
			if (o.getCustomerId() == null || o.getCustomerId() != userId) {
				throw new BusinessException(ErrorCode.PAYMENT_FORBIDDEN, "Khong co quyen xem giao dich nay.");
			}
			List<OrderPayment> list = orderPaymentRepository.findByOrderIdWithOrderAndBranch(orderId);
			OrderPayment p = list.stream()
					.filter(x -> "zalopay".equalsIgnoreCase(x.getPaymentMethod()))
					.max(Comparator.comparing(OrderPayment::getId))
					.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
							"Khong tim thay giao dich ZaloPay cho don."));
			JsonNode gw = zaloPayService.queryOrderStatus(cfg, p.getTransactionRef());
			boolean synced = maybeCompleteOrderPaymentFromZaloQuery(p, gw);
			OrderPayment fresh = orderPaymentRepository.findById(p.getId()).orElse(p);
			return new ZaloPayStatusResponse(gw, fresh.getStatus(), synced);
		}
		Deposit d = depositRepository.findById(depositId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_NOT_FOUND, "Khong tim thay coc."));
		if (d.getCustomerId() != userId) {
			throw new BusinessException(ErrorCode.DEPOSIT_ACCESS_DENIED, "Khong co quyen xem coc nay.");
		}
		if (!isZaloDeposit(d)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Coc khong phai ZaloPay.");
		}
		if (d.getGatewayTxnRef() == null || d.getGatewayTxnRef().isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thieu ma giao dich ZaloPay (gateway_txn_ref).");
		}
		JsonNode gw = zaloPayService.queryOrderStatus(cfg, d.getGatewayTxnRef());
		boolean synced = maybeCompleteDepositFromZaloQuery(d, gw);
		Deposit fresh = depositRepository.findById(d.getId()).orElse(d);
		return new ZaloPayStatusResponse(gw, fresh.getStatus(), synced);
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
				return Map.of("return_code", -1, "return_message", "mac not equal");
			}
			JsonNode payload = zaloPayService.parseCallbackDataJson(data);
			String appTransId = payload.path("app_trans_id").asText("");
			if (appTransId.isBlank()) {
				return Map.of("return_code", -2, "return_message", "missing app_trans_id");
			}
			long amount = payload.path("amount").asLong(-1);
			OrderPayment p = orderPaymentRepository.findByTransactionRef(appTransId).orElse(null);
			if (p != null) {
				if ("Completed".equals(p.getStatus())) {
					return Map.of("return_code", 1, "return_message", "success");
				}
				if (amount < 0 || BigDecimal.valueOf(amount).compareTo(p.getAmount()) != 0) {
					return Map.of("return_code", -4, "return_message", "amount mismatch");
				}
			boolean paidOrder = payload.hasNonNull("zp_trans_id") || payload.path("result").asInt(0) == 1;
			if (!paidOrder) {
				if ("Pending".equals(p.getStatus())) {
					p.setStatus("Failed");
					orderPaymentRepository.save(p);
				}
				return Map.of("return_code", 1, "return_message", "success");
			}
				completePaymentAndOrder(p, "zalopay", null);
				return Map.of("return_code", 1, "return_message", "success");
			}
			Deposit d = depositRepository.findByGatewayTxnRef(appTransId).orElse(null);
			if (d == null) {
				return Map.of("return_code", -3, "return_message", "order not found");
			}
			String depStatus = d.getStatus();
			if ("Confirmed".equals(depStatus) || "Cancelled".equals(depStatus)) {
				return Map.of("return_code", 1, "return_message", "success");
			}
			if (!"Pending".equals(depStatus) && !"AwaitingPayment".equals(depStatus)) {
				return Map.of("return_code", 1, "return_message", "success");
			}
			if (amount < 0 || BigDecimal.valueOf(amount).compareTo(d.getAmount()) != 0) {
				return Map.of("return_code", -4, "return_message", "amount mismatch");
			}
			JsonNode rcNode = payload.get("return_code");
			Integer rcParsed = parseZaloReturnCode(rcNode);
			boolean hasReturnCode = rcParsed != null;
			if (hasReturnCode) {
				int rc = rcParsed;
				if (rc == 3) {
					log.info("ZaloPay callback dang xu ly app_trans_id={} depositId={}", appTransId, d.getId());
					return Map.of("return_code", 1, "return_message", "success");
				}
				if (rc == 2) {
					depositService.cancelPendingDepositAfterOnlinePaymentDeclined(d.getId());
					return Map.of("return_code", 1, "return_message", "success");
				}
				if (rc == 1) {
					boolean paidDep = payload.hasNonNull("zp_trans_id") || payload.path("result").asInt(0) == 1;
					if (!paidDep) {
						return Map.of("return_code", 1, "return_message", "success");
					}
					if (payload.hasNonNull("zp_trans_id")) {
						JsonNode z = payload.get("zp_trans_id");
						d.setGatewayOrderUrl(z.isTextual() ? z.asText() : String.valueOf(z.asLong()));
					}
					completeDepositAfterOnlinePayment(d, null);
					return Map.of("return_code", 1, "return_message", "success");
				}
				log.warn("ZaloPay callback return_code={} app_trans_id={} depositId={}", rc, appTransId, d.getId());
				return Map.of("return_code", 1, "return_message", "success");
			}
			boolean paidDepLegacy = payload.hasNonNull("zp_trans_id") || payload.path("result").asInt(0) == 1;
			if (!paidDepLegacy) {
				depositService.cancelPendingDepositAfterOnlinePaymentDeclined(d.getId());
				return Map.of("return_code", 1, "return_message", "success");
			}
			if (payload.hasNonNull("zp_trans_id")) {
				JsonNode z = payload.get("zp_trans_id");
				d.setGatewayOrderUrl(z.isTextual() ? z.asText() : String.valueOf(z.asLong()));
			}
			completeDepositAfterOnlinePayment(d, null);
			return Map.of("return_code", 1, "return_message", "success");
		}
		catch (Exception e) {
			return Map.of("return_code", -9, "return_message", "parse error");
		}
	}

	private static boolean isZaloDeposit(Deposit d) {
		String gw = d.getPaymentGateway();
		if (gw != null && gw.toLowerCase().contains("zalo")) {
			return true;
		}
		return "zalopay".equalsIgnoreCase(d.getPaymentMethod());
	}

	private boolean maybeCompleteOrderPaymentFromZaloQuery(OrderPayment p, JsonNode gw) {
		if (!"Pending".equals(p.getStatus())) {
			return false;
		}
		if (gw.path("return_code").asInt() != 1) {
			return false;
		}
		if (gw.path("zp_trans_id").asLong(0) <= 0) {
			return false;
		}
		long amt = gw.path("amount").asLong(-1);
		if (amt < 0 || BigDecimal.valueOf(amt).compareTo(p.getAmount()) != 0) {
			return false;
		}
		completePaymentAndOrder(p, "zalopay", null);
		return true;
	}

	private boolean maybeCompleteDepositFromZaloQuery(Deposit d, JsonNode gw) {
		// Xu ly ca Pending va AwaitingPayment
		if (!"Pending".equals(d.getStatus()) && !"AwaitingPayment".equals(d.getStatus())) {
			return false;
		}
		if (gw.path("return_code").asInt() != 1) {
			return false;
		}
		if (gw.path("zp_trans_id").asLong(0) <= 0) {
			return false;
		}
		long amt = gw.path("amount").asLong(-1);
		if (amt < 0 || BigDecimal.valueOf(amt).compareTo(d.getAmount()) != 0) {
			return false;
		}
		JsonNode zpNode = gw.get("zp_trans_id");
		String zps = zpNode != null && zpNode.isIntegralNumber() ? String.valueOf(zpNode.asLong())
				: gw.path("zp_trans_id").asText("");
		if (!zps.isBlank()) {
			d.setGatewayOrderUrl(zps);
		}
		completeDepositAfterOnlinePayment(d, null);
		return true;
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

	// Thực hiện khi thanh toán online thành công:
	// B1: Load xe, kiểm tra còn available
	// B2: Chuyển deposit AwaitingPayment → Pending
	// B3: Tạo FinancialTransaction
	// B4: Set xe RESERVED
	private void completeDepositAfterOnlinePayment(Deposit d, String vnpGatewayTransactionNo) {
		// B1: Kiem tra xe van con Available
		Vehicle v = vehicleRepository.findById(d.getVehicleId())
			.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Xe không tìm thấy."));

		// Neu xe da bi RESERVED/SOLD boi nguoi khac trong luc user dang thanh toan
		if (!VehicleStatus.AVAILABLE.getDbValue().equals(v.getStatus())
				&& !VehicleStatus.RESERVED.getDbValue().equals(v.getStatus())) {
			// Xe da ban hoac khong kha dung -> cancel deposit, can hoan tien thu cong
			d.setStatus("Cancelled");
			String n = d.getNotes() != null ? d.getNotes() + " | " : "";
			d.setNotes(n + "Huy: Xe khong con kha dung khi payment callback (can hoan tien)");
			depositRepository.save(d);
			log.warn("Deposit {} payment confirmed but vehicle {} no longer available. Manual refund needed.",
					d.getId(), d.getVehicleId());
			return;
		}

		// B2: Chuyen deposit tu AwaitingPayment → Pending
		d.setStatus("Pending");
		if (vnpGatewayTransactionNo != null && !vnpGatewayTransactionNo.isBlank()
				&& (d.getGatewayOrderUrl() == null || d.getGatewayOrderUrl().isBlank())) {
			d.setGatewayOrderUrl(vnpGatewayTransactionNo.trim());
		}
		depositRepository.save(d);

		// B3: Tao FinancialTransaction (luc nay moi tao vi payment confirmed)
		boolean txExists = financialTransactionRepository
			.findByReferenceTypeAndReferenceId("Deposit", d.getId()).isPresent();
		if (!txExists) {
			FinancialTransaction tx = new FinancialTransaction();
			tx.setUserId(d.getCustomerId());
			tx.setType("Deposit");
			tx.setAmount(d.getAmount());
			tx.setStatus("Completed");
			tx.setDescription("Dat coc xe thanh toan online #" + d.getId());
			tx.setReferenceId(d.getId());
			tx.setReferenceType("Deposit");
			financialTransactionRepository.save(tx);
		} else {
			financialTransactionRepository.findByReferenceTypeAndReferenceId("Deposit", d.getId())
				.ifPresent(tx -> {
					tx.setStatus("Completed");
					financialTransactionRepository.save(tx);
				});
		}

		// B4: Set xe RESERVED (chi xay ra khi payment thanh cong)
		v.setStatus(VehicleStatus.RESERVED.getDbValue());
		vehicleRepository.save(v);
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

	private static Integer parseZaloReturnCode(JsonNode n) {
		if (n == null || n.isNull()) {
			return null;
		}
		if (n.isInt() || n.isLong()) {
			return n.asInt();
		}
		if (n.isTextual()) {
			try {
				return Integer.parseInt(n.asText().trim());
			}
			catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}
}
