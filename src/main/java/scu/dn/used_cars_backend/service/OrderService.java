package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.audit.AuditLogWriter;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.sales.AddManualPaymentRequest;
import scu.dn.used_cars_backend.dto.sales.CancelOrderRequest;
import scu.dn.used_cars_backend.dto.sales.CreateOrderRequest;
import scu.dn.used_cars_backend.dto.sales.CreateOrderResponse;
import scu.dn.used_cars_backend.dto.sales.OrderDetailDto;
import scu.dn.used_cars_backend.dto.sales.OrderPaymentRowDto;
import scu.dn.used_cars_backend.dto.sales.OrderRowDto;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.FinancialTransaction;
import scu.dn.used_cars_backend.entity.OrderPayment;
import scu.dn.used_cars_backend.entity.SalesOrder;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleStatus;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.FinancialTransactionRepository;
import scu.dn.used_cars_backend.repository.OrderPaymentRepository;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class OrderService {

	private static final String ROLE_CUSTOMER = "CUSTOMER";
	private static final String ROLE_ADMIN = "ADMIN";

	private final SalesOrderRepository salesOrderRepository;
	private final VehicleRepository vehicleRepository;
	private final DepositRepository depositRepository;
	private final FinancialTransactionRepository financialTransactionRepository;
	private final OrderPaymentRepository orderPaymentRepository;
	private final UserRepository userRepository;
	private final StaffService staffService;
	private final VehicleService vehicleService;
	private final DepositService depositService;
	private final InAppNotificationService inAppNotificationService;
	private final EmailNotificationService emailNotificationService;
	private final scu.dn.used_cars_backend.service.payment.PaymentApplicationService paymentApplicationService;
	private final AuditLogWriter auditLogWriter;

	@Transactional
	public CreateOrderResponse create(long actorUserId, String jwtRole, CreateOrderRequest req) {
		// B1: lock xe (FOR UPDATE) + check ton tai + check quyen chi nhanh
		Vehicle v = vehicleRepository.findByIdAndDeletedFalseForUpdate(req.getVehicleId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		assertStaffOrAdminOnVehicle(actorUserId, jwtRole, v);

		// B2: check trang thai xe hop le (chi Available hoac Reserved moi tao duoc don)
		String vst = v.getStatus();
		if (!VehicleStatus.AVAILABLE.getDbValue().equals(vst) && !VehicleStatus.RESERVED.getDbValue().equals(vst)) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Xe không đủ điều kiện tạo đơn.");
		}

		// B3: CHAN "1 xe - 1 order active" (Rule 3)
		// Neu xe da co don Pending/Processing -> reject, khong cho tao don moi
		long activeOrderCount = salesOrderRepository.countByVehicleIdAndStatusIn(
				v.getId(), List.of("Pending", "Processing"));
		if (activeOrderCount > 0) {
			throw new BusinessException(ErrorCode.VEHICLE_HAS_ACTIVE_ORDER,
					"Xe đã có đơn hàng đang xử lý, không thể tạo thêm đơn.");
		}

		// B4: CHONG BAN SAI CHU (Rule 2 - deposit ownership)
		// Neu xe dang co coc active cua khach nao -> chi khach do moi duoc mua
		List<Deposit> activeDeposits = depositRepository.findByVehicleIdAndStatusIn(
				v.getId(), List.of("Confirmed", "AwaitingPayment", "Pending"));
		if (!activeDeposits.isEmpty()) {
			Deposit owner = activeDeposits.get(0);
			if (owner.getCustomerId() != req.getCustomerId()) {
				throw new BusinessException(ErrorCode.DEPOSIT_OWNER_MISMATCH,
						"Xe đang được khách khác đặt cọc, không thể bán cho khách này.");
			}
			// Xe da co coc cua dung khach -> request BUOC phai di kem depositId
			if (req.getDepositId() == null) {
				throw new BusinessException(ErrorCode.DEPOSIT_REQUIRED,
						"Xe đã có phiếu cọc — vui lòng chọn phiếu cọc tương ứng khi tạo đơn.");
			}
		}

		// B5: check khach hang ton tai
		userRepository.findActiveByIdWithRoles(req.getCustomerId())
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy khách hàng."));

		// B6: neu co depositId -> lock coc va validate ky
		BigDecimal depAmt = BigDecimal.ZERO;
		Deposit dep = null;
		if (req.getDepositId() != null) {
			// Lock FOR UPDATE de tranh race condition (2 luong cung convert 1 coc)
			dep = depositRepository.findByIdForUpdate(req.getDepositId())
					.orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_NOT_FOUND, "Không tìm thấy cọc."));
			if (dep.getCustomerId() != req.getCustomerId() || !dep.getVehicleId().equals(req.getVehicleId())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Cọc không khớp khách hoặc xe.");
			}
			if (!"Confirmed".equals(dep.getStatus())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Cọc phải đã xác nhận.");
			}
			// Coc da duoc dung cho don khac -> reject (double-convert protection)
			if (dep.getOrderId() != null) {
				throw new BusinessException(ErrorCode.DEPOSIT_ALREADY_CONVERTED,
						"Cọc đã được dùng cho đơn khác, không thể tái sử dụng.");
			}
			depAmt = dep.getAmount();
		}
		BigDecimal total = req.getTotalPrice();
		String num = nextOrderNumber();
		SalesOrder o = new SalesOrder();
		o.setOrderNumber(num);
		o.setCustomerId(req.getCustomerId());
		o.setStaffId(actorUserId);
		o.setBranch(v.getBranch());
		o.setVehicle(v);
		o.setTotalPrice(total);
		o.setDepositAmount(depAmt);
		o.setRemainingAmount(total.subtract(depAmt));
		o.setPaymentMethod(req.getPaymentMethod());
		o.setNotes(req.getNotes());
		o.setStatus("Pending");
		o.setCreatedBy(actorUserId);
		salesOrderRepository.save(o);
		if (dep != null) {
			dep.setStatus("Converted");
			dep.setOrderId(o.getId());
			depositRepository.save(dep);
		}
		v.setStatus(VehicleStatus.RESERVED.getDbValue());
		vehicleRepository.save(v);
		vehicleService.evictPublicVehicleCaches(v.getId());
		// Khong insert FinancialTransaction o day nua (model event-based):
		// Transaction se duoc tao khi tien thuc su vao qua OrderPayment (cash/VNPay/ZaloPay).
		// Tranh double-count voi row Deposit da co.

		// Gui email xac nhan don hang cho khach
		User customer = userRepository.findById(req.getCustomerId()).orElse(null);
		if (customer != null) {
			emailNotificationService.sendOrderCreatedEmailAsync(o, v, customer);
		}
		// Gui thong bao in-app cho khach
		String vehicleLabel = (v.getTitle() != null && !v.getTitle().isBlank())
				? v.getTitle() : (v.getCategory() != null ? v.getCategory().getName() : "xe");
		inAppNotificationService.createNotification(
				req.getCustomerId(), "order_created",
				"Đơn hàng của bạn đã được tạo",
				"Đơn hàng #" + num + " cho " + vehicleLabel + " đã được tạo.",
				"/dashboard/orders");

		return CreateOrderResponse.builder().id(o.getId()).orderNumber(num).status(o.getStatus()).build();
	}

	@Transactional(readOnly = true)
	public List<OrderRowDto> list(long actorUserId, String jwtRole, String status, String search, int page, int size) {
		PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
		String st = blankToNull(status);
		String se = blankToNull(search);
		Page<SalesOrder> pg = switch (jwtRole) {
			case ROLE_CUSTOMER -> salesOrderRepository.pageForCustomer(actorUserId, st, se != null ? se : "", pr);
			case ROLE_ADMIN -> salesOrderRepository.pageAll(st, se != null ? se : "", pr);
			default -> {
				int bid = staffService.getManagerBranchId(actorUserId);
				yield salesOrderRepository.pageForBranch(bid, st, se != null ? se : "", pr);
			}
		};
		return pg.getContent().stream().map(this::toRow).toList();
	}

	@Transactional(readOnly = true)
	public Map<String, Object> listMeta(long actorUserId, String jwtRole, String status, String search, int page,
			int size) {
		PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
		String st = blankToNull(status);
		String se = blankToNull(search);
		Page<SalesOrder> pg = switch (jwtRole) {
			case ROLE_CUSTOMER -> salesOrderRepository.pageForCustomer(actorUserId, st, se != null ? se : "", pr);
			case ROLE_ADMIN -> salesOrderRepository.pageAll(st, se != null ? se : "", pr);
			default -> {
				int bid = staffService.getManagerBranchId(actorUserId);
				yield salesOrderRepository.pageForBranch(bid, st, se != null ? se : "", pr);
			}
		};
		Map<String, Object> m = new HashMap<>();
		m.put("totalElements", pg.getTotalElements());
		m.put("totalPages", pg.getTotalPages());
		m.put("page", pg.getNumber());
		m.put("size", pg.getSize());
		return m;
	}

	@Transactional(readOnly = true)
	public OrderDetailDto getById(long actorUserId, String jwtRole, long orderId) {
		SalesOrder o = salesOrderRepository.findByIdWithGraph(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn."));
		assertCanViewOrder(actorUserId, jwtRole, o);
		List<OrderPayment> pays = orderPaymentRepository.findByOrderIdWithOrderAndBranch(orderId);
		List<OrderPaymentRowDto> payRows = pays.stream().map(this::toPayRow).toList();
		return toDetail(o, payRows);
	}

	@Transactional
	public void advanceStatus(long actorUserId, String jwtRole, long orderId) {
		SalesOrder o = salesOrderRepository.findByIdWithBranch(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn."));
		assertStaffOrAdminOnOrder(actorUserId, jwtRole, o);
		if (!"Pending".equals(o.getStatus())) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS_TRANSITION, "Chỉ đơn Chờ xử lý mới chuyển tiếp.");
		}
		o.setStatus("Processing");
		salesOrderRepository.save(o);
		vehicleService.evictPublicVehicleCaches(o.getVehicle().getId());
	}

	@Transactional
	public void cancel(long actorUserId, String jwtRole, long orderId, CancelOrderRequest body) {
		SalesOrder o = salesOrderRepository.findByIdWithGraph(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn."));
		assertCanCancelOrder(actorUserId, jwtRole, o);
		if (!"Pending".equals(o.getStatus()) && !"Processing".equals(o.getStatus())) {
			throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL, "Đơn không thể hủy ở trạng thái này.");
		}
		o.setStatus("Cancelled");
		if (body != null && body.getReason() != null && !body.getReason().isBlank()) {
			String n = o.getNotes() != null ? o.getNotes() + " | " : "";
			o.setNotes(n + "Huy: " + body.getReason().trim());
		}
		salesOrderRepository.save(o);
		Vehicle v = o.getVehicle();
		v.setStatus(VehicleStatus.AVAILABLE.getDbValue());
		vehicleRepository.save(v);
		// Hoan tien coc: neu la online (VNPay) -> goi API hoan ngay, neu cash -> tu dong xong
		depositRepository.findByOrderId(orderId).ifPresent(d -> {
			d.setStatus("RefundPending");
			depositRepository.save(d);
			boolean refunded = paymentApplicationService.refundDeposit(d);
			if (refunded) {
				d.setStatus("Refunded");
				// Ghi row Refund vao Transactions khi hoan coc thanh cong
				insertRefundTransaction(d.getCustomerId(), "Deposit", d.getId(),
						d.getAmount(), d.getPaymentGateway(),
						"Hoan coc #" + d.getId() + " khi huy don #" + o.getOrderNumber());
			} else {
				d.setStatus("RefundFailed");
			}
			depositRepository.save(d);
		});
		depositService.closeBlockingDepositsWhenOrderCancelled(v.getId(), o.getCustomerId(), orderId);
		financialTransactionRepository.findByReferenceTypeAndReferenceId("Order", orderId).ifPresent(tx -> {
			tx.setStatus("Failed");
			financialTransactionRepository.save(tx);
		});
		vehicleService.evictPublicVehicleCaches(v.getId());
	}

	@Transactional
	public void confirmSold(long actorUserId, String jwtRole, long orderId) {
		SalesOrder o = salesOrderRepository.findByIdWithGraph(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn."));
		assertStaffOrAdminOnOrder(actorUserId, jwtRole, o);
		if (!"Processing".equals(o.getStatus())) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS_TRANSITION, "Chỉ đơn Đang xử lý mới xác nhận bán.");
		}
		o.setStatus("Completed");
		o.setRemainingAmount(BigDecimal.ZERO);
		salesOrderRepository.save(o);
		Vehicle v = o.getVehicle();
		v.setStatus(VehicleStatus.SOLD.getDbValue());
		vehicleRepository.save(v);
		financialTransactionRepository.findByReferenceTypeAndReferenceId("Order", orderId).ifPresent(tx -> {
			tx.setStatus("Completed");
			financialTransactionRepository.save(tx);
		});
		vehicleService.evictPublicVehicleCaches(v.getId());
		inAppNotificationService.createNotification(o.getCustomerId(), "order", "Xe đã được bàn giao",
				"Đơn hàng đã hoàn tất — xe đã được bàn giao thành công.", "/dashboard/orders");
	}

	@Transactional
	public void addManualPayment(long actorUserId, String jwtRole, long orderId, AddManualPaymentRequest req) {
		SalesOrder o = salesOrderRepository.findByIdWithGraph(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn."));
		assertStaffOrAdminOnOrder(actorUserId, jwtRole, o);
		if ("Cancelled".equals(o.getStatus()) || "Completed".equals(o.getStatus())) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS_TRANSITION, "Đơn không nhận thanh toán.");
		}
		if (req.getAmount().compareTo(o.getRemainingAmount()) > 0) {
			throw new BusinessException(ErrorCode.PAYMENT_EXCEEDS_REMAINING, "Vượt số tiền còn lại.");
		}
		String pm = req.getPaymentMethod().trim().toLowerCase();
		if ("bank_transfer".equals(pm)) {
			pm = "cash";
		}
		OrderPayment p = new OrderPayment();
		p.setOrder(o);
		p.setAmount(req.getAmount());
		p.setPaymentMethod(pm);
		p.setStatus("Completed");
		String ref = req.getTransactionRef() != null ? req.getTransactionRef().trim() : null;
		p.setTransactionRef(ref != null && !ref.isEmpty() ? ref : null);
		p.setPaidAt(Instant.now());
		orderPaymentRepository.save(p);
		if ("cash".equals(pm) && (p.getTransactionRef() == null || p.getTransactionRef().isBlank())) {
			String autoRef = "CASH-O" + orderId + "-P" + p.getId() + "-" + Long.toHexString(System.nanoTime());
			if (autoRef.length() > 100) {
				autoRef = autoRef.substring(0, 100);
			}
			p.setTransactionRef(autoRef);
			orderPaymentRepository.save(p);
		}
		o.setRemainingAmount(o.getRemainingAmount().subtract(req.getAmount()));
		salesOrderRepository.save(o);

		// Model event-based: moi lan thu tien tao 1 row Transactions rieng (ref = OrderPayment).
		FinancialTransaction tx = new FinancialTransaction();
		tx.setUserId(o.getCustomerId());
		tx.setType("Purchase");
		tx.setAmount(req.getAmount());
		tx.setStatus("Completed");
		tx.setDescription("Thanh toan don hang #" + o.getOrderNumber() + " (thu cong)");
		tx.setReferenceId(p.getId());
		tx.setReferenceType("OrderPayment");
		tx.setPaymentGateway(normalizePaymentGateway(pm));
		financialTransactionRepository.save(tx);
	}

	// Chuan hoa payment_gateway de khop CK_Trans_PaymentGateway (vnpay|zalopay|cash).
	// Bat ky gia tri nao khong phai 'vnpay'/'zalopay' (sau lowercase) se map thanh 'cash'.
	private String normalizePaymentGateway(String paymentMethod) {
		if (paymentMethod == null) {
			return "cash";
		}
		String v = paymentMethod.trim().toLowerCase();
		if ("vnpay".equals(v) || "zalopay".equals(v)) {
			return v;
		}
		return "cash";
	}

	// Tao 1 row Transactions type='Refund' khi hoan tien thanh cong.
	// referenceType = 'Deposit' hoac 'OrderPayment', referenceId = id cua entity duoc hoan.
	private void insertRefundTransaction(Long userId, String referenceType, Long referenceId,
			java.math.BigDecimal amount, String gateway, String description) {
		FinancialTransaction tx = new FinancialTransaction();
		tx.setUserId(userId);
		tx.setType("Refund");
		tx.setAmount(amount);
		tx.setStatus("Completed");
		tx.setDescription(description);
		tx.setReferenceId(referenceId);
		tx.setReferenceType(referenceType);
		tx.setPaymentGateway(normalizePaymentGateway(gateway));
		financialTransactionRepository.save(tx);
	}

	private String nextOrderNumber() {
		String ymd = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.BASIC_ISO_DATE);
		String max = salesOrderRepository.findMaxOrderNumberForYmd(ymd);
		int next = 1;
		if (max != null && max.contains("-")) {
			String tail = max.substring(max.lastIndexOf('-') + 1);
			try {
				next = Integer.parseInt(tail) + 1;
			}
			catch (NumberFormatException ignored) {
				next = 1;
			}
		}
		return String.format("ORD-%s-%04d", ymd, next);
	}

	private void assertStaffOrAdminOnVehicle(long actorUserId, String jwtRole, Vehicle v) {
		if (ROLE_ADMIN.equals(jwtRole)) {
			return;
		}
		int bid = staffService.getManagerBranchId(actorUserId);
		if (v.getBranch().getId() != bid) {
			throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "Xe không thuộc chi nhánh của bạn.");
		}
	}

	private void assertCanViewOrder(long actorUserId, String jwtRole, SalesOrder o) {
		if (ROLE_ADMIN.equals(jwtRole)) {
			return;
		}
		if (ROLE_CUSTOMER.equals(jwtRole)) {
			if (o.getCustomerId() != actorUserId) {
				throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "Không có quyền xem đơn này.");
			}
			return;
		}
		int bid = staffService.getManagerBranchId(actorUserId);
		if (o.getBranch().getId() != bid) {
			throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "Không có quyền xem đơn này.");
		}
	}

	private void assertStaffOrAdminOnOrder(long actorUserId, String jwtRole, SalesOrder o) {
		if (ROLE_ADMIN.equals(jwtRole)) {
			return;
		}
		int bid = staffService.getManagerBranchId(actorUserId);
		if (o.getBranch().getId() != bid) {
			throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "Đơn không thuộc chi nhánh của bạn.");
		}
	}

	private void assertCanCancelOrder(long actorUserId, String jwtRole, SalesOrder o) {
		if (ROLE_ADMIN.equals(jwtRole)) {
			return;
		}
		if (ROLE_CUSTOMER.equals(jwtRole)) {
			if (o.getCustomerId() != actorUserId) {
				throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "Không có quyền hủy đơn này.");
			}
			return;
		}
		assertStaffOrAdminOnOrder(actorUserId, jwtRole, o);
	}

	private OrderRowDto toRow(SalesOrder o) {
		User cust = userRepository.findByIdAndDeletedFalse(o.getCustomerId()).orElse(null);
		User st = o.getStaffId() != null ? userRepository.findByIdAndDeletedFalse(o.getStaffId()).orElse(null) : null;
		return OrderRowDto.builder()
				.id(o.getId())
				.orderNumber(o.getOrderNumber())
				.customerId(o.getCustomerId())
				.customerName(cust != null ? cust.getName() : "-")
				.staffId(o.getStaffId())
				.staffName(st != null ? st.getName() : null)
				.branchId(o.getBranch().getId())
				.branchName(o.getBranch().getName())
				.vehicleId(o.getVehicle().getId())
				.vehicleTitle(o.getVehicle().getTitle())
				.totalPrice(o.getTotalPrice().toPlainString())
				.depositAmount(o.getDepositAmount().toPlainString())
				.remainingAmount(o.getRemainingAmount().toPlainString())
				.status(o.getStatus())
				.createdAt(o.getCreatedAt().toString())
				.build();
	}

	private OrderDetailDto toDetail(SalesOrder o, List<OrderPaymentRowDto> payments) {
		User cust = userRepository.findByIdAndDeletedFalse(o.getCustomerId()).orElse(null);
		User st = o.getStaffId() != null ? userRepository.findByIdAndDeletedFalse(o.getStaffId()).orElse(null) : null;
		return OrderDetailDto.builder()
				.id(o.getId())
				.orderNumber(o.getOrderNumber())
				.customerId(o.getCustomerId())
				.customerName(cust != null ? cust.getName() : "-")
				.staffId(o.getStaffId())
				.staffName(st != null ? st.getName() : null)
				.staffEmail(st != null ? st.getEmail() : null)
				.staffPhone(st != null ? st.getPhone() : null)
				.branchId(o.getBranch().getId())
				.branchName(o.getBranch().getName())
				.vehicleId(o.getVehicle().getId())
				.vehicleTitle(o.getVehicle().getTitle())
				.totalPrice(o.getTotalPrice().toPlainString())
				.depositAmount(o.getDepositAmount().toPlainString())
				.remainingAmount(o.getRemainingAmount().toPlainString())
				.paymentMethod(o.getPaymentMethod())
				.status(o.getStatus())
				.notes(o.getNotes())
				.createdAt(o.getCreatedAt().toString())
				.updatedAt(o.getUpdatedAt().toString())
				.payments(payments)
				.build();
	}

	private OrderPaymentRowDto toPayRow(OrderPayment p) {
		return OrderPaymentRowDto.builder()
				.id(p.getId())
				.paymentMethod(p.getPaymentMethod())
				.status(p.getStatus())
				.amount(p.getAmount().toPlainString())
				.transactionRef(p.getTransactionRef())
				.paidAt(p.getPaidAt() != null ? p.getPaidAt().toString() : null)
				.createdAt(p.getCreatedAt().toString())
				.build();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	// ====== SCHEDULER SUPPORT: auto-timeout don Pending khong coc ======

	// Tra ve danh sach id cua don Pending (depositAmount = 0) tao truoc cutoff.
	// Dung cho OrderTimeoutScheduler - giu ham ben service de tach boundary transaction.
	@Transactional(readOnly = true)
	public List<Long> findPendingDirectOrderIdsCreatedBefore(Instant cutoff) {
		return salesOrderRepository.findPendingDirectOrdersCreatedBefore(cutoff)
				.stream().map(SalesOrder::getId).toList();
	}

	// Tu dong huy 1 don Pending khong coc da qua han:
	// B1: lock xe FOR UPDATE
	// B2: kiem tra lai trang thai don (phong race voi cancel tay)
	// B3: chuyen don -> Cancelled, xe Reserved -> Available, FT -> Failed
	// B4: ghi audit log
	@Transactional
	public void autoCancelTimedOutDirectOrder(long orderId) {
		SalesOrder o = salesOrderRepository.findByIdWithGraph(orderId)
				.orElse(null);
		if (o == null || !"Pending".equals(o.getStatus())) {
			return; // da xu ly boi luong khac
		}
		// Lock xe truoc khi thay doi
		Vehicle v = vehicleRepository.findByIdForUpdate(o.getVehicle().getId())
				.orElse(null);
		if (v == null) {
			return;
		}
		o.setStatus("Cancelled");
		String note = "[AUTO-TIMEOUT] Don Pending khong coc qua han tu dong huy.";
		o.setNotes(o.getNotes() != null ? o.getNotes() + " | " + note : note);
		salesOrderRepository.save(o);

		if (VehicleStatus.RESERVED.getDbValue().equals(v.getStatus())) {
			v.setStatus(VehicleStatus.AVAILABLE.getDbValue());
			vehicleRepository.save(v);
			vehicleService.evictPublicVehicleCaches(v.getId());
		}

		financialTransactionRepository.findByReferenceTypeAndReferenceId("Order", orderId).ifPresent(tx -> {
			tx.setStatus("Failed");
			financialTransactionRepository.save(tx);
		});

		// Ghi audit log de trace (module="orders", action gan voi REST style)
		auditLogWriter.persist(null, "system", "orders",
				"AUTO_TIMEOUT_CANCEL /api/v1/orders/" + orderId,
				"Auto cancel don Pending khong coc qua han (orderNumber=" + o.getOrderNumber() + ")",
				null);
	}
}
