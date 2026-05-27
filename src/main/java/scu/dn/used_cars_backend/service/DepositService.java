package scu.dn.used_cars_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.BranchPublicAccessSupport;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.sales.CancelDepositRequest;
import scu.dn.used_cars_backend.dto.sales.CreateDepositRequest;
import scu.dn.used_cars_backend.dto.sales.CreateDepositResponse;
import scu.dn.used_cars_backend.dto.sales.DepositListItemDto;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.FinancialTransaction;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleImage;
import scu.dn.used_cars_backend.entity.VehicleStatus;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.FinancialTransactionRepository;
import scu.dn.used_cars_backend.repository.SystemConfigRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.service.payment.PaymentGatewayConfigService;
import scu.dn.used_cars_backend.service.payment.VnpayService;
import scu.dn.used_cars_backend.service.payment.ZaloPayService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepositService {

	private static final Logger log = LoggerFactory.getLogger(DepositService.class);
	private static final String ROLE_CUSTOMER = "CUSTOMER";
	private static final String ROLE_ADMIN = "ADMIN";
	private static final String NOTE_SYNC_AVAILABLE =
			"Huy: Dong bo khi showroom dat xe ve Dang ban (Pending/AwaitingPayment)";
	private static final String NOTE_ORDER_CANCEL = "Huy: Dong bo khi huy don hang #";
	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final DateTimeFormatter ZP_TRANS_DAY = DateTimeFormatter.ofPattern("yyMMdd");
	private final DepositRepository depositRepository;
	private final VehicleRepository vehicleRepository;
	private final UserRepository userRepository;
	private final FinancialTransactionRepository financialTransactionRepository;
	private final StaffService staffService;
	private final PaymentGatewayConfigService paymentGatewayConfigService;
	private final VnpayService vnpayService;
	private final ZaloPayService zaloPayService;
	private final ObjectMapper objectMapper;
	private final VehicleService vehicleService;
	private final InAppNotificationService inAppNotificationService;
	private final EmailNotificationService emailNotificationService;
	private final ShowroomCustomerService showroomCustomerService;
	private final SystemConfigRepository systemConfigRepository;

	@Transactional(rollbackFor = Exception.class)
	public CreateDepositResponse create(long actorUserId, String jwtRole, CreateDepositRequest req, String clientIp) {
		// B1: Resolve customer va validate
		long customerId = resolveCustomerId(actorUserId, jwtRole, req);
		validateActiveCustomer(customerId);

		// B2: Load xe voi lock de tranh race condition (tim ca xe bi an, validate sau)
		Vehicle v = vehicleRepository.findByIdForUpdate(req.getVehicleId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		if (v.isDeleted()) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Xe đã bị ẩn khỏi hệ thống, không thể đặt cọc.");
		}
		BranchPublicAccessSupport.assertPubliclyAccessible(v.getBranch());
		assertActorCanUseVehicle(actorUserId, jwtRole, v);
		releaseStaleReservedVehicleIfNeeded(v);

		// B3: Kiem tra xe con Available
		if (!VehicleStatus.AVAILABLE.getDbValue().equals(v.getStatus())) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Xe không khả dụng để đặt cọc.");
		}

		if (depositRepository.countByVehicleIdAndStatusIn(v.getId(), List.of("Pending", "Confirmed", "AwaitingPayment")) > 0) {
			throw new BusinessException(ErrorCode.VEHICLE_ALREADY_DEPOSITED, "Xe đã có đặt cọc đang hiệu lực.");
		}

		LocalDate depositDate = parseDateOrToday(req.getDepositDate());
		LocalDate expiryDate = parseExpiry(req.getExpiryDate(), depositDate);
		String pm = normalizeAndAssertDepositPayment(jwtRole, req.getPaymentMethod());

		// B4: Validate so tien coc >= X% gia xe (doc tu config)
		validateDepositAmountPercent(req.getAmount(), v);

		// B5: Tao deposit record
		Deposit d = new Deposit();
		d.setCustomerId(customerId);
		d.setVehicleId(v.getId());
		d.setAmount(req.getAmount());
		d.setPaymentMethod(pm);
		d.setDepositDate(depositDate);
		d.setExpiryDate(expiryDate);
		d.setNotes(req.getNote());
		d.setCreatedBy(actorUserId);

		// B6: Phan biet online vs cash
		if ("vnpay".equals(pm) || "zalopay".equals(pm)) {
			d.setStatus("AwaitingPayment");
			depositRepository.save(d);
			v.setStatus(VehicleStatus.RESERVED.getDbValue());
			vehicleRepository.save(v);
			vehicleService.evictPublicVehicleCaches(v.getId());
		} else {
			// Cash/offline: ghi nhận cọc ngay (không cần duyệt chi nhánh)
			d.setStatus("Confirmed");
			depositRepository.save(d);
			FinancialTransaction tx = new FinancialTransaction();
			tx.setUserId(customerId);
			tx.setType("Deposit");
			tx.setAmount(req.getAmount());
			tx.setStatus("Completed");
			tx.setDescription("Dat coc xe #" + d.getId());
			tx.setReferenceId(d.getId());
			tx.setReferenceType("Deposit");
			tx.setPaymentGateway("cash");
			financialTransactionRepository.save(tx);
			v.setStatus(VehicleStatus.RESERVED.getDbValue());
			vehicleRepository.save(v);
			vehicleService.evictPublicVehicleCaches(v.getId());
			d.setPaymentGateway("cash");
			String cashRef = "CASH-D" + d.getId() + "-" + Long.toHexString(System.nanoTime());
			if (cashRef.length() > 100) {
				cashRef = cashRef.substring(0, 100);
			}
			d.setGatewayTxnRef(cashRef);
			depositRepository.save(d);
			finalizeDepositAsConfirmed(d, true);
			User customer = userRepository.findById(customerId).orElse(null);
			if (customer != null) {
				emailNotificationService.sendDepositSuccessEmailAsync(d, v, customer);
			}
		}

		// B7: Build payment URL cho online
		String paymentUrl = null;
		if ("vnpay".equals(pm)) {
			var cfg = paymentGatewayConfigService.loadVnpayForCreate();
			String txnRef = "D" + d.getId() + "T" + Long.toHexString(System.nanoTime());
			d.setPaymentGateway("vnpay");
			d.setGatewayTxnRef(txnRef);
			String info = "Dat coc id " + d.getId();
			VnpayService.VnpayPayUrlResult built = vnpayService.buildPaymentUrl(cfg, txnRef, req.getAmount(), info,
					clientIp);
			d.setGatewayTransRef(built.vnpCreateDate());
			depositRepository.save(d);
			paymentUrl = built.paymentUrl();
		}
		else if ("zalopay".equals(pm)) {
			var cfg = paymentGatewayConfigService.loadZaloPayForCreate();
			String appTransId = newZaloAppTransIdForDeposit(d.getId());
			d.setPaymentGateway("zalopay");
			d.setGatewayTxnRef(appTransId);
			depositRepository.save(d);
			String base = paymentGatewayConfigService.frontendBaseUrl().replaceAll("/$", "");
			String redirect = base + "/payment/result?kind=zalo_deposit&depositId=" + d.getId() + "&vehicleId=" + v.getId();
			String embed;
			try {
				embed = objectMapper.writeValueAsString(Map.of("redirecturl", redirect));
			}
			catch (JsonProcessingException e) {
				throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo embed_data ZaloPay.");
			}
			String orderUrl = null;
			for (int attempt = 0; attempt < 2; attempt++) {
				try {
					orderUrl = zaloPayService.createOrderAndGetPayUrl(cfg, appTransId, req.getAmount().longValueExact(),
							String.valueOf(customerId), "Dat coc xe #" + d.getId(), embed);
					break;
				}
				catch (BusinessException ex) {
					if (attempt == 0 && ErrorCode.VALIDATION_FAILED.equals(ex.getErrorCode())) {
						appTransId = newZaloAppTransIdForDeposit(d.getId());
						d.setGatewayTxnRef(appTransId);
						depositRepository.save(d);
						log.info("ZaloPay create deposit: đổi app_trans_id và thử lại — {}", appTransId);
						continue;
					}
					throw ex;
				}
			}
			if (orderUrl == null || orderUrl.isBlank()) {
				throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "ZaloPay thiếu order_url.");
			}
			d.setGatewayOrderUrl(orderUrl);
			depositRepository.save(d);
			paymentUrl = orderUrl;
		}

		if (("vnpay".equals(pm) || "zalopay".equals(pm))
				&& (paymentUrl == null || paymentUrl.isBlank())) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
					"Không tạo được liên kết thanh toán. Kiểm tra cấu hình VNPay/ZaloPay.");
		}

		return CreateDepositResponse.builder()
				.id(d.getId())
				.vehicleId(v.getId())
				.amount(d.getAmount().toPlainString())
				.status(d.getStatus())
				.paymentUrl(paymentUrl)
				.depositDate(d.getDepositDate().toString())
				.expiryDate(d.getExpiryDate().toString())
				.build();
	}

	@Transactional(rollbackFor = Exception.class)
	public CreateDepositResponse resumeOnlinePayment(long actorUserId, String jwtRole, long depositId, String clientIp) {
		if (!ROLE_CUSTOMER.equals(jwtRole)) {
			throw new BusinessException(ErrorCode.DEPOSIT_ACCESS_DENIED,
					"Chỉ tài khoản khách hàng mới được tiếp tục thanh toán cọc online.");
		}
		Deposit d = depositRepository.findById(depositId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_NOT_FOUND, "Không tìm thấy cọc."));
		assertCanModifyDeposit(actorUserId, jwtRole, d);
		if (cancelIfExpiredOnlineDeposit(depositId)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Cọc đã hết hạn thanh toán. Vui lòng hủy và đặt cọc lại nếu xe còn trống.");
		}
		d = depositRepository.findById(depositId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_NOT_FOUND, "Không tìm thấy cọc."));
		if (!"AwaitingPayment".equals(d.getStatus())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Chỉ cọc đang chờ thanh toán online mới được tiếp tục.");
		}
		String gw = d.getPaymentGateway();
		if (gw == null || gw.isBlank()) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Thiếu thông tin cổng thanh toán.");
		}
		String gwTrim = gw.trim();
		if ("vnpay".equalsIgnoreCase(gwTrim)) {
			String txnRef = d.getGatewayTxnRef();
			if (txnRef == null || txnRef.isBlank()) {
				throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Thiếu mã giao dịch VNPay.");
			}
			var cfg = paymentGatewayConfigService.loadVnpayForCreate();
			String info = "Dat coc id " + d.getId();
			VnpayService.VnpayPayUrlResult built = vnpayService.buildPaymentUrl(cfg, txnRef, d.getAmount(), info,
					clientIp);
			d.setGatewayTransRef(built.vnpCreateDate());
			depositRepository.save(d);
			return CreateDepositResponse.builder()
					.id(d.getId())
					.vehicleId(d.getVehicleId())
					.amount(d.getAmount().toPlainString())
					.status(d.getStatus())
					.paymentUrl(built.paymentUrl())
					.depositDate(d.getDepositDate().toString())
					.expiryDate(d.getExpiryDate().toString())
					.build();
		}
		if ("zalopay".equalsIgnoreCase(gwTrim)) {
			String appTransId = d.getGatewayTxnRef();
			if (appTransId == null || appTransId.isBlank()) {
				throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Thiếu mã giao dịch ZaloPay.");
			}
			appTransId = appTransId.trim();
			var cfg = paymentGatewayConfigService.loadZaloPayForCreate();
			String base = paymentGatewayConfigService.frontendBaseUrl().replaceAll("/$", "");
			String redirect = base + "/payment/result?kind=zalo_deposit&depositId=" + d.getId() + "&vehicleId="
					+ d.getVehicleId();
			String embed;
			try {
				embed = objectMapper.writeValueAsString(Map.of("redirecturl", redirect));
			}
			catch (JsonProcessingException e) {
				throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo embed_data ZaloPay.");
			}
			String orderUrl = null;
			for (int attempt = 0; attempt < 2; attempt++) {
				try {
					orderUrl = zaloPayService.createOrderAndGetPayUrl(cfg, appTransId, d.getAmount().longValueExact(),
							String.valueOf(d.getCustomerId()), "Dat coc xe #" + d.getId(), embed);
					break;
				}
				catch (BusinessException ex) {
					if (attempt == 0 && ErrorCode.VALIDATION_FAILED.equals(ex.getErrorCode())) {
						appTransId = newZaloAppTransIdForDeposit(d.getId());
						d.setGatewayTxnRef(appTransId);
						depositRepository.save(d);
						log.info("ZaloPay resume: đổi app_trans_id và thử lại — {}", appTransId);
						continue;
					}
					throw ex;
				}
			}
			if (orderUrl == null || orderUrl.isBlank()) {
				throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "ZaloPay thiếu order_url.");
			}
			d.setGatewayOrderUrl(orderUrl);
			depositRepository.save(d);
			return CreateDepositResponse.builder()
					.id(d.getId())
					.vehicleId(d.getVehicleId())
					.amount(d.getAmount().toPlainString())
					.status(d.getStatus())
					.paymentUrl(orderUrl)
					.depositDate(d.getDepositDate().toString())
					.expiryDate(d.getExpiryDate().toString())
					.build();
		}
		throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Cổng thanh toán không hỗ trợ tiếp tục từ đây.");
	}

	@Transactional(readOnly = true)
	public List<DepositListItemDto> page(long actorUserId, String jwtRole, String status, int page, int size) {
		PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
		Page<Deposit> pg = switch (jwtRole) {
			case ROLE_CUSTOMER -> depositRepository.pageForCustomerVisible(actorUserId, blankToNull(status), pr);
			case ROLE_ADMIN -> depositRepository.pageAll(blankToNull(status), pr);
			default -> {
				int bid = staffService.getManagerBranchId(actorUserId);
				yield depositRepository.pageForBranch(bid, blankToNull(status), pr);
			}
		};
		List<Deposit> rows = pg.getContent();
		Set<Long> vids = rows.stream().map(Deposit::getVehicleId).collect(Collectors.toCollection(LinkedHashSet::new));
		Set<Long> cids = rows.stream().map(Deposit::getCustomerId).collect(Collectors.toCollection(LinkedHashSet::new));
		Map<Long, VehicleDepositRowInfo> vmap = loadVehicleRowInfo(vids);
		Map<Long, CustomerDepositRowInfo> customerInfo = loadCustomerInfo(cids);
		return rows.stream()
				.map(d -> toListItem(d, vmap.get(d.getVehicleId()),
						customerInfo.get(d.getCustomerId())))
				.toList();
	}

	@Transactional(readOnly = true)
	public Map<String, Object> pageMeta(long actorUserId, String jwtRole, String status, int page, int size) {
		PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
		Page<Deposit> pg = switch (jwtRole) {
			case ROLE_CUSTOMER -> depositRepository.pageForCustomerVisible(actorUserId, blankToNull(status), pr);
			case ROLE_ADMIN -> depositRepository.pageAll(blankToNull(status), pr);
			default -> {
				int bid = staffService.getManagerBranchId(actorUserId);
				yield depositRepository.pageForBranch(bid, blankToNull(status), pr);
			}
		};
		Map<String, Object> m = new HashMap<>();
		m.put("totalElements", pg.getTotalElements());
		m.put("totalPages", pg.getTotalPages());
		m.put("page", pg.getNumber());
		m.put("size", pg.getSize());
		return m;
	}

	@Transactional(rollbackFor = Exception.class)
	public DepositListItemDto getById(long actorUserId, String jwtRole, long depositId) {
		Deposit d = depositRepository.findById(depositId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_NOT_FOUND, "Không tìm thấy cọc."));
		assertCanViewDeposit(actorUserId, jwtRole, d);
		if (ROLE_CUSTOMER.equals(jwtRole) && cancelIfExpiredOnlineDeposit(depositId)) {
			d = depositRepository.findById(depositId).orElse(d);
		}
		Map<Long, VehicleDepositRowInfo> vmap = loadVehicleRowInfo(Set.of(d.getVehicleId()));
		Map<Long, CustomerDepositRowInfo> customerInfo = loadCustomerInfo(Set.of(d.getCustomerId()));
		return toListItem(d, vmap.get(d.getVehicleId()), customerInfo.get(d.getCustomerId()));
	}

	@Transactional(rollbackFor = Exception.class)
	public void cancel(long actorUserId, String jwtRole, long depositId, CancelDepositRequest body) {
		Deposit d = depositRepository.findById(depositId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_NOT_FOUND, "Không tìm thấy cọc."));
		assertCanModifyDeposit(actorUserId, jwtRole, d);
		if ("Confirmed".equals(d.getStatus())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Cọc đã đặt cọc — dùng yêu cầu hủy có lý do (cancel-confirmed).");
		}
		boolean awaitingOnline = "AwaitingPayment".equals(d.getStatus()) && d.getPaymentGateway() != null
				&& ("vnpay".equalsIgnoreCase(d.getPaymentGateway().trim())
						|| "zalopay".equalsIgnoreCase(d.getPaymentGateway().trim()));
		if (!"Pending".equals(d.getStatus()) && !awaitingOnline) {
			throw new BusinessException(ErrorCode.DEPOSIT_CANNOT_CANCEL, "Trạng thái cọc không cho phép hủy.");
		}
		if (body != null && body.getReason() != null && !body.getReason().isBlank()) {
			String n = d.getNotes() != null ? d.getNotes() + " | " : "";
			d.setNotes(n + "Huy: " + body.getReason().trim());
		}
		finalizeDepositCancellation(d);
	}

	@Transactional(rollbackFor = Exception.class)
	public void cancelConfirmedDeposit(long actorUserId, String jwtRole, long depositId, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Lý do hủy là bắt buộc.");
		}
		Deposit d = depositRepository.findById(depositId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_NOT_FOUND, "Không tìm thấy cọc."));
		if (!"Confirmed".equals(d.getStatus())) {
			throw new BusinessException(ErrorCode.DEPOSIT_CANNOT_CANCEL,
					"Chỉ có thể hủy cọc đã xác nhận (Confirmed) bằng luồng này.");
		}
		boolean admin = ROLE_ADMIN.equals(jwtRole);
		boolean owner = ROLE_CUSTOMER.equals(jwtRole) && d.getCustomerId() == actorUserId;
		if (!admin && !owner) {
			throw new BusinessException(ErrorCode.DEPOSIT_ACCESS_DENIED, "Không có quyền hủy cọc đã xác nhận.");
		}
		String trimmed = reason.trim();
		Instant now = Instant.now();
		String audit = "HUY_CONFIRMED|by=" + actorUserId + "|at=" + now + "|reason=" + trimmed.replace('|', '/');
		String n = d.getNotes() != null ? d.getNotes() + " | " : "";
		d.setNotes(n + audit);
		finalizeDepositCancellation(d);
	}

	// Cho phep manager/admin danh dau thu cong deposit la "Da hoan coc"
	// Su dung khi gateway khong ho tro refund tu dong (sandbox) hoac da hoan tien ngoai he thong
	@Transactional(rollbackFor = Exception.class)
	public void markRefundedManually(long actorUserId, String jwtRole, long depositId) {
		if (ROLE_CUSTOMER.equals(jwtRole)) {
			throw new BusinessException(ErrorCode.DEPOSIT_ACCESS_DENIED, "Khach hang khong co quyen thao tac nay.");
		}
		Deposit d = depositRepository.findById(depositId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_NOT_FOUND, "Khong tim thay coc."));
		if (!"RefundPending".equals(d.getStatus()) && !"RefundFailed".equals(d.getStatus())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Chi co the danh dau hoan coc cho deposit dang RefundPending hoac RefundFailed.");
		}
		Instant now = Instant.now();
		String audit = "MANUAL_REFUND|by=" + actorUserId + "|at=" + now;
		String n = d.getNotes() != null ? d.getNotes() + " | " : "";
		d.setNotes(n + audit);
		d.setStatus("Refunded");
		depositRepository.save(d);
		// Ghi row Refund vao Transactions
		insertRefundTransaction(d.getCustomerId(), "Deposit", d.getId(),
				d.getAmount(), d.getPaymentGateway(),
				"Hoan coc thu cong #" + d.getId());
		log.info("Deposit {} danh dau Refunded thu cong boi user {}.", depositId, actorUserId);
	}

	@Transactional(rollbackFor = Exception.class)
	public void syncOpenDepositsWhenVehicleSetAvailable(long vehicleId) {
		long confirmed = depositRepository.countByVehicleIdAndStatusIn(vehicleId, List.of("Confirmed"));
		if (confirmed > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Xe vẫn có cọc đã xác nhận (Confirmed). Hãy hủy cọc / hoàn tiền qua quy trình trước khi mở bán lại.");
		}
		List<Long> toClose = depositRepository
				.findByVehicleIdAndStatusIn(vehicleId, List.of("AwaitingPayment", "Pending")).stream()
				.map(Deposit::getId)
				.toList();
		for (Long depId : toClose) {
			depositRepository.findById(depId).ifPresent(d -> {
				if (!"Pending".equals(d.getStatus()) && !"AwaitingPayment".equals(d.getStatus())) {
					return;
				}
				String n = d.getNotes() != null ? d.getNotes() + " | " : "";
				d.setNotes(n + NOTE_SYNC_AVAILABLE);
				finalizeDepositCancellation(d);
			});
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void closeBlockingDepositsWhenOrderCancelled(long vehicleId, long customerId, long orderId) {
		for (Deposit d : depositRepository.findByVehicleIdAndStatusIn(vehicleId, List.of("AwaitingPayment"))) {
			String n = d.getNotes() != null ? d.getNotes() + " | " : "";
			d.setNotes(n + NOTE_ORDER_CANCEL + orderId + "|dong checkout online");
			finalizeDepositCancellation(d);
		}
		for (Deposit d : depositRepository.findByVehicleIdAndStatusIn(vehicleId, List.of("Pending"))) {
			if (d.getCustomerId() != customerId) {
				continue;
			}
			String n = d.getNotes() != null ? d.getNotes() + " | " : "";
			d.setNotes(n + NOTE_ORDER_CANCEL + orderId);
			finalizeDepositCancellation(d);
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void cancelPendingDepositAfterOnlinePaymentDeclined(long depositId) {
		Deposit d = depositRepository.findById(depositId).orElse(null);
		if (d == null) {
			return;
		}
		// Xu ly ca AwaitingPayment va Pending
		if (!"Pending".equals(d.getStatus()) && !"AwaitingPayment".equals(d.getStatus())) {
			log.warn("Attempted to cancel deposit {} via payment failure flow. Status: {}", depositId,
					d.getStatus());
			return;
		}
		String n = d.getNotes() != null ? d.getNotes() + " | " : "";
		d.setNotes(n + "Huy: Thanh toan khong thanh cong (tu dong)");
		finalizeDepositCancellation(d);
	}

	private void finalizeDepositCancellation(Deposit d) {
		d.setStatus("Cancelled");
		depositRepository.save(d);
		depositRepository.flush();
		// Chi update FinancialTransaction neu co (AwaitingPayment khong co transaction)
		financialTransactionRepository.findByReferenceTypeAndReferenceId("Deposit", d.getId()).ifPresent(tx -> {
			tx.setStatus("Failed");
			financialTransactionRepository.save(tx);
		});
		long stillActive = depositRepository.countByVehicleIdAndStatusIn(d.getVehicleId(),
				List.of("Pending", "Confirmed", "AwaitingPayment"));
		if (stillActive == 0) {
			vehicleRepository.findById(d.getVehicleId()).ifPresent(v -> {
				if (VehicleStatus.RESERVED.getDbValue().equals(v.getStatus())) {
					v.setStatus(VehicleStatus.AVAILABLE.getDbValue());
					vehicleRepository.save(v);
					vehicleRepository.flush();
				}
			});
		}
		// Luôn evict cache khi cancel cọc, vì listingHoldActive có thể thay đổi
		vehicleService.evictPublicVehicleCaches(d.getVehicleId());
	}

	@Transactional(rollbackFor = Exception.class)
	public void cancelPendingOnlineDepositTimedOut(long depositId) {
		Deposit d = depositRepository.findById(depositId).orElse(null);
		if (d == null || !"AwaitingPayment".equals(d.getStatus())) {
			return;
		}
		String gw = d.getPaymentGateway();
		if (gw == null || (!"vnpay".equalsIgnoreCase(gw.trim()) && !"zalopay".equalsIgnoreCase(gw.trim()))) {
			return;
		}
		String n = d.getNotes() != null ? d.getNotes() + " | " : "";
		d.setNotes(n + "Huy: Qua thoi han thanh toan online (tu dong)");
		finalizeDepositCancellation(d);
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean cancelIfExpiredOnlineDeposit(long depositId) {
		Deposit d = depositRepository.findById(depositId).orElse(null);
		if (d == null || !"AwaitingPayment".equals(d.getStatus())) return false;
		String gw = d.getPaymentGateway();
		if (gw == null || gw.isBlank()) return false;
		if (d.getCreatedAt() == null) return false;
		long minutesAgo = Duration.between(d.getCreatedAt(), Instant.now()).toMinutes();
		int timeoutMinutes;
		try {
			String raw = paymentGatewayConfigService
					.getOptional(PaymentGatewayConfigService.KEY_DEPOSIT_ONLINE_PAYMENT_TIMEOUT_MINUTES);
			timeoutMinutes = parseOnlinePaymentTimeoutMinutes(raw);
		}
		catch (Exception e) {
			log.warn("Không đọc được timeout cọc online, dùng fallback 6 phút: {}", e.toString());
			timeoutMinutes = 6;
		}
		if (minutesAgo < timeoutMinutes) return false;
		String n = d.getNotes() != null ? d.getNotes() + " | " : "";
		d.setNotes(n + "Huy: Qua thoi han thanh toan online (tu dong khi query)");
		finalizeDepositCancellation(d);
		return true;
	}

	@Transactional(readOnly = true)
	public List<Long> findPendingOnlineDepositIdsExpiredBefore(Instant cutoff) {
		return depositRepository.findPendingOnlineDepositIdsCreatedBefore(cutoff);
	}

	public static int parseOnlinePaymentTimeoutMinutes(String raw) {
		if (raw == null || raw.isBlank()) {
			return 5;
		}
		try {
			int m = Integer.parseInt(raw.trim());
			return m >= 1 && m <= 24 * 60 ? m : 5;
		}
		catch (NumberFormatException e) {
			return 5;
		}
	}

	/**
	 * Chuyển cọc sang Confirmed sau thanh toán / tạo cọc tiền mặt (không cần duyệt showroom).
	 */
	@Transactional(rollbackFor = Exception.class)
	public void finalizeDepositAsConfirmed(Deposit d, boolean notifyCustomer) {
		d.setStatus("Confirmed");
		depositRepository.save(d);
		financialTransactionRepository.findByReferenceTypeAndReferenceId("Deposit", d.getId()).ifPresent(tx -> {
			tx.setStatus("Completed");
			financialTransactionRepository.save(tx);
		});
		if (notifyCustomer) {
			inAppNotificationService.createNotification(d.getCustomerId(), "deposit", "Đặt cọc thành công",
					"Khoản đặt cọc của bạn đã được ghi nhận.", "/dashboard/deposits");
		}
	}

	private long resolveCustomerId(long actorUserId, String jwtRole, CreateDepositRequest req) {
		if (ROLE_CUSTOMER.equals(jwtRole)) {
			return actorUserId;
		}
		boolean hasCustomerId = req.getCustomerId() != null;
		boolean hasShowroom = req.getShowroomCustomer() != null;
		if (hasCustomerId == hasShowroom) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Phải cung cấp customerId hoặc showroomCustomer (không cả hai, không bỏ trống).");
		}
		if (hasShowroom) {
			return showroomCustomerService.findOrCreate(req.getShowroomCustomer());
		}
		return req.getCustomerId();
	}

	private void validateActiveCustomer(long userId) {
		User u = userRepository.findActiveByIdWithRoles(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy khách hàng."));
		boolean isCustomer = u.getUserRoles().stream().anyMatch(ur -> "Customer".equals(ur.getRole().getName()));
		if (!isCustomer) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Người dùng không phải khách hàng.");
		}
		ProfileCompletionSupport.assertCustomerProfileComplete(u);
	}

	private void releaseStaleReservedVehicleIfNeeded(Vehicle v) {
		if (!VehicleStatus.RESERVED.getDbValue().equals(v.getStatus())) {
			return;
		}
		long active = depositRepository.countByVehicleIdAndStatusIn(v.getId(),
				List.of("Pending", "Confirmed", "AwaitingPayment"));
		if (active == 0) {
			v.setStatus(VehicleStatus.AVAILABLE.getDbValue());
			vehicleRepository.save(v);
			vehicleService.evictPublicVehicleCaches(v.getId());
		}
	}

	private void assertActorCanUseVehicle(long actorUserId, String jwtRole, Vehicle v) {
		if (ROLE_ADMIN.equals(jwtRole) || ROLE_CUSTOMER.equals(jwtRole)) {
			return;
		}
		int bid;
		try {
			bid = staffService.getManagerBranchId(actorUserId);
		} catch (BusinessException e) {
			if (ErrorCode.BRANCH_NOT_FOUND.equals(e.getErrorCode())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Tài khoản của bạn chưa được gán vào chi nhánh nào. Liên hệ Admin để thiết lập.");
			}
			throw e;
		}
		if (v.getBranch().getId() != bid) {
			throw new BusinessException(ErrorCode.DEPOSIT_ACCESS_DENIED, "Xe không thuộc chi nhánh của bạn.");
		}
	}

	private void assertCanViewDeposit(long actorUserId, String jwtRole, Deposit d) {
		if (ROLE_ADMIN.equals(jwtRole)) {
			return;
		}
		if (ROLE_CUSTOMER.equals(jwtRole)) {
			if (d.getCustomerId() != actorUserId) {
				throw new BusinessException(ErrorCode.DEPOSIT_ACCESS_DENIED, "Không có quyền xem cọc này.");
			}
			return;
		}
		Vehicle v = vehicleRepository.findById(d.getVehicleId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		int bid = staffService.getManagerBranchId(actorUserId);
		if (v.getBranch().getId() != bid) {
			throw new BusinessException(ErrorCode.DEPOSIT_ACCESS_DENIED, "Không có quyền xem cọc này.");
		}
	}

	private void assertCanModifyDeposit(long actorUserId, String jwtRole, Deposit d) {
		if (ROLE_ADMIN.equals(jwtRole)) {
			return;
		}
		if (ROLE_CUSTOMER.equals(jwtRole)) {
			if (d.getCustomerId() != actorUserId) {
				throw new BusinessException(ErrorCode.DEPOSIT_ACCESS_DENIED, "Không có quyền hủy cọc này.");
			}
			return;
		}
		assertCanViewDeposit(actorUserId, jwtRole, d);
	}

	private Map<Long, CustomerDepositRowInfo> loadCustomerInfo(Set<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		List<User> users = userRepository.findAllByIdInAndDeletedFalse(userIds);
		Map<Long, CustomerDepositRowInfo> out = new HashMap<>();
		for (User u : users) {
			String n = u.getName();
			String name = n != null && !n.isBlank() ? n : "-";
			String phone = u.getPhone();
			String phoneOut = phone != null && !phone.isBlank() ? phone.trim() : null;
			out.put(u.getId(), new CustomerDepositRowInfo(name, phoneOut));
		}
		return out;
	}

	private Map<Long, VehicleDepositRowInfo> loadVehicleRowInfo(Set<Long> vehicleIds) {
		if (vehicleIds.isEmpty()) {
			return Map.of();
		}
		List<Vehicle> vs = vehicleRepository.findAllByIdInWithImages(vehicleIds);
		Map<Long, VehicleDepositRowInfo> out = new HashMap<>();
		for (Vehicle v : vs) {
			out.put(v.getId(), new VehicleDepositRowInfo(v.getTitle(), pickPrimaryVehicleImageUrl(v)));
		}
		return out;
	}

	private static String pickPrimaryVehicleImageUrl(Vehicle v) {
		List<VehicleImage> imgs = v.getImages();
		if (imgs == null || imgs.isEmpty()) {
			return null;
		}
		return imgs.stream()
				.filter(VehicleImage::isPrimaryImage)
				.map(VehicleImage::getImageUrl)
				.findFirst()
				.orElseGet(() -> imgs.stream()
						.min(Comparator.comparingInt(VehicleImage::getSortOrder))
						.map(VehicleImage::getImageUrl)
						.orElse(null));
	}

	private static String newZaloAppTransIdForDeposit(long depositId) {
		String appTransId = LocalDate.now(VN).format(ZP_TRANS_DAY) + "_" + depositId + "_"
				+ Long.toHexString(System.nanoTime());
		if (appTransId.length() > 40) {
			appTransId = appTransId.substring(0, 40);
		}
		return appTransId;
	}

	private DepositListItemDto toListItem(Deposit d, VehicleDepositRowInfo vi, CustomerDepositRowInfo ci) {
		String customerName = ci != null ? ci.name() : "-";
		String customerPhone = ci != null ? ci.phone() : null;
		String vehicleTitle = vi != null ? vi.title() : "-";
		String vehicleImageUrl = vi != null ? vi.imageUrl() : null;
		String st = d.getStatus();
		if ("Converted".equals(st)) {
			st = "ConvertedToOrder";
		}
		String oid = d.getOrderId() == null ? null : String.valueOf(d.getOrderId());
		String gwRef = d.getGatewayTxnRef();
		if (gwRef != null) {
			gwRef = gwRef.trim();
			if (gwRef.isEmpty()) {
				gwRef = null;
			}
		}
		return DepositListItemDto.builder()
				.id(String.valueOf(d.getId()))
				.vehicleId(String.valueOf(d.getVehicleId()))
				.customerId(String.valueOf(d.getCustomerId()))
				.customerName(customerName)
				.customerPhone(customerPhone)
				.vehicleTitle(vehicleTitle)
				.vehicleImageUrl(vehicleImageUrl)
				.amount(d.getAmount().longValue())
				.depositDate(d.getDepositDate().toString())
				.expiryDate(d.getExpiryDate().toString())
				.createdAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null)
				.status(st)
				.orderId(oid)
				.gatewayTxnRef(gwRef)
				.build();
	}

	private record VehicleDepositRowInfo(String title, String imageUrl) {
	}

	private record CustomerDepositRowInfo(String name, String phone) {
	}

	private String normalizeAndAssertDepositPayment(String jwtRole, String methodRaw) {
		String raw = methodRaw == null ? "" : methodRaw.trim();
		if (raw.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Phương thức thanh toán là bắt buộc.");
		}
		String pm = raw.toLowerCase(Locale.ROOT);
		if ("bank_transfer".equals(pm)) {
			if (ROLE_CUSTOMER.equals(jwtRole)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Đặt cọc trên website chỉ hỗ trợ VNPay hoặc ZaloPay.");
			}
			pm = "cash";
		}
		if (ROLE_CUSTOMER.equals(jwtRole)) {
			if (!"vnpay".equals(pm) && !"zalopay".equals(pm)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Đặt cọc trên website chỉ hỗ trợ VNPay hoặc ZaloPay.");
			}
			if ("vnpay".equals(pm)) {
				paymentGatewayConfigService.assertVnpayEnabled();
			}
			if ("zalopay".equals(pm)) {
				paymentGatewayConfigService.assertZaloPayEnabled();
			}
			return pm;
		}
		switch (pm) {
			case "cash" -> paymentGatewayConfigService.assertCashPaymentAllowed();
			case "vnpay" -> paymentGatewayConfigService.assertVnpayEnabled();
			case "zalopay" -> paymentGatewayConfigService.assertZaloPayEnabled();
			default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Phương thức không hợp lệ. Chọn: tiền mặt (cash), VNPay hoặc ZaloPay.");
		}
		return pm;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static LocalDate parseDateOrToday(String s) {
		if (s == null || s.isBlank()) {
			return LocalDate.now();
		}
		try {
			return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
		}
		catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "depositDate không hợp lệ.");
		}
	}

	private static LocalDate parseExpiry(String s, LocalDate depositDate) {
		if (s == null || s.isBlank()) {
			return depositDate.plusDays(7);
		}
		try {
			return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
		}
		catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "expiryDate không hợp lệ.");
		}
	}

	// Tao 1 row Transactions type='Refund' khi hoan tien thanh cong.
	void insertRefundTransaction(Long userId, String referenceType, Long referenceId,
			BigDecimal amount, String gateway, String description) {
		String gw = (gateway != null) ? gateway.trim().toLowerCase() : "cash";
		if (!"vnpay".equals(gw) && !"zalopay".equals(gw)) {
			gw = "cash";
		}
		FinancialTransaction tx = new FinancialTransaction();
		tx.setUserId(userId);
		tx.setType("Refund");
		tx.setAmount(amount);
		tx.setStatus("Completed");
		tx.setDescription(description);
		tx.setReferenceId(referenceId);
		tx.setReferenceType(referenceType);
		tx.setPaymentGateway(gw);
		financialTransactionRepository.save(tx);
	}

	private void validateDepositAmountPercent(BigDecimal depositAmount, Vehicle vehicle) {
		if (vehicle.getPrice() == null || vehicle.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}
		int percent = 10;
		String raw = systemConfigRepository.findByConfigKey(PaymentGatewayConfigService.KEY_DEPOSIT_MIN_PERCENT)
				.map(r -> r.getConfigValue() != null ? r.getConfigValue().trim() : "")
				.orElse("");
		if (!raw.isEmpty()) {
			try {
				percent = Integer.parseInt(raw);
			} catch (NumberFormatException ignored) {
			}
		}
		if (percent <= 0 || percent > 100) {
			percent = 10;
		}
		BigDecimal minAmount = vehicle.getPrice()
				.multiply(BigDecimal.valueOf(percent))
				.divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.CEILING);
		if (depositAmount.compareTo(minAmount) < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Số tiền cọc tối thiểu là " + percent + "% giá xe (" + minAmount.toPlainString() + " VNĐ).");
		}
	}
}
