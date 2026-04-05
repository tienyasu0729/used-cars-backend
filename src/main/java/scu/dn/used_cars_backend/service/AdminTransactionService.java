package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.admin.TimelineEventDto;
import scu.dn.used_cars_backend.dto.admin.TransactionDetailDto;
import scu.dn.used_cars_backend.dto.admin.TransactionRowDto;
import scu.dn.used_cars_backend.dto.admin.TransactionSummaryDto;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.OrderPayment;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.OrderPaymentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTransactionService {

	public static final String SOURCE_DEPOSIT = "DEPOSIT";
	public static final String SOURCE_ORDER_PAYMENT = "ORDER_PAYMENT";

	private static final int MAX_PAGE_SIZE = 50;
	private static final int MAX_EXPORT_ROWS = 5000;
	private static final int MAX_FETCH_PER_SOURCE = 5000;

	private final DepositRepository depositRepository;
	private final OrderPaymentRepository orderPaymentRepository;
	private final UserRepository userRepository;
	private final VehicleRepository vehicleRepository;
	private final StaffService staffService;

	public record TransactionFilter(
			String source,
			String status,
			String gateway,
			Long branchId,
			String keyword,
			Instant fromInclusive,
			Instant toExclusive,
			int page,
			int size) {
	}

	public record PagedRows(List<TransactionRowDto> items, PageMetaDto meta) {
	}

	public PagedRows page(TransactionFilter filter, long actorUserId, boolean isAdmin) {
		int size = Math.min(Math.max(filter.size(), 1), MAX_PAGE_SIZE);
		int page = Math.max(filter.page(), 0);
		String statusBucket = toStatusBucket(filter.status());
		String gateway = normalizeGatewayParam(filter.gateway());
		String kw = StringUtils.hasText(filter.keyword()) ? filter.keyword().trim().toLowerCase(Locale.ROOT) : null;

		boolean includeDeposit = !StringUtils.hasText(filter.source())
				|| SOURCE_DEPOSIT.equalsIgnoreCase(filter.source());
		boolean includeOrder = !StringUtils.hasText(filter.source())
				|| SOURCE_ORDER_PAYMENT.equalsIgnoreCase(filter.source());

		Integer branchId = resolveBranchIdForQuery(filter.branchId(), actorUserId, isAdmin);

		int fetchPerSource = Math.min(MAX_FETCH_PER_SOURCE, (page + 1) * size);

		List<TransactionRowDto> merged = new ArrayList<>();
		if (includeDeposit) {
			if (branchId == null) {
				merged.addAll(mapDeposits(depositRepository
						.pageAllForTransactionHistory(statusBucket, filter.fromInclusive(), filter.toExclusive(),
								gateway, PageRequest.of(0, fetchPerSource))
						.getContent()));
			} else {
				merged.addAll(mapDeposits(depositRepository
						.pageForBranchForTransactionHistory(branchId, statusBucket, filter.fromInclusive(),
								filter.toExclusive(), gateway, PageRequest.of(0, fetchPerSource))
						.getContent()));
			}
		}
		if (includeOrder) {
			if (branchId == null) {
				merged.addAll(mapOrderPayments(orderPaymentRepository
						.pageAllWithOrder(statusBucket, filter.fromInclusive(), filter.toExclusive(), gateway,
								PageRequest.of(0, fetchPerSource))
						.getContent()));
			} else {
				merged.addAll(mapOrderPayments(orderPaymentRepository
						.pageByBranchWithOrder(branchId, statusBucket, filter.fromInclusive(), filter.toExclusive(),
								gateway, PageRequest.of(0, fetchPerSource))
						.getContent()));
			}
		}

		merged.sort(Comparator.comparing(TransactionRowDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

		if (kw != null) {
			merged = merged.stream().filter(r -> matchesKeyword(r, kw)).toList();
		}

		long totalElements;
		if (kw == null) {
			long cd = branchId == null
					? depositRepository.countAllForTransactionHistory(statusBucket, filter.fromInclusive(),
							filter.toExclusive(), gateway)
					: depositRepository.countForBranchForTransactionHistory(branchId, statusBucket,
							filter.fromInclusive(), filter.toExclusive(), gateway);
			long co = branchId == null
					? orderPaymentRepository.countAllWithFilters(statusBucket, filter.fromInclusive(),
							filter.toExclusive(), gateway)
					: orderPaymentRepository.countByBranchWithFilters(branchId, statusBucket, filter.fromInclusive(),
							filter.toExclusive(), gateway);
			if (!includeDeposit) {
				cd = 0;
			}
			if (!includeOrder) {
				co = 0;
			}
			totalElements = cd + co;
		} else {
			totalElements = merged.size();
		}

		int from = page * size;
		List<TransactionRowDto> slice;
		if (from >= merged.size()) {
			slice = List.of();
		} else {
			int to = Math.min(from + size, merged.size());
			slice = merged.subList(from, to);
		}

		int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
		PageMetaDto meta = PageMetaDto.builder()
				.page(page)
				.size(size)
				.totalElements(totalElements)
				.totalPages(totalPages)
				.build();
		return new PagedRows(slice, meta);
	}

	public TransactionSummaryDto summary(TransactionFilter filter, long actorUserId, boolean isAdmin) {
		String statusBucket = toStatusBucket(filter.status());
		String gateway = normalizeGatewayParam(filter.gateway());
		String kw = StringUtils.hasText(filter.keyword()) ? filter.keyword().trim().toLowerCase(Locale.ROOT) : null;
		boolean includeDeposit = !StringUtils.hasText(filter.source())
				|| SOURCE_DEPOSIT.equalsIgnoreCase(filter.source());
		boolean includeOrder = !StringUtils.hasText(filter.source())
				|| SOURCE_ORDER_PAYMENT.equalsIgnoreCase(filter.source());
		Integer branchId = resolveBranchIdForQuery(filter.branchId(), actorUserId, isAdmin);

		List<TransactionRowDto> merged = new ArrayList<>();
		if (includeDeposit) {
			if (branchId == null) {
				merged.addAll(mapDeposits(depositRepository
						.pageAllForTransactionHistory(statusBucket, filter.fromInclusive(), filter.toExclusive(),
								gateway, PageRequest.of(0, MAX_FETCH_PER_SOURCE))
						.getContent()));
			} else {
				merged.addAll(mapDeposits(depositRepository
						.pageForBranchForTransactionHistory(branchId, statusBucket, filter.fromInclusive(),
								filter.toExclusive(), gateway, PageRequest.of(0, MAX_FETCH_PER_SOURCE))
						.getContent()));
			}
		}
		if (includeOrder) {
			if (branchId == null) {
				merged.addAll(mapOrderPayments(orderPaymentRepository
						.pageAllWithOrder(statusBucket, filter.fromInclusive(), filter.toExclusive(), gateway,
								PageRequest.of(0, MAX_FETCH_PER_SOURCE))
						.getContent()));
			} else {
				merged.addAll(mapOrderPayments(orderPaymentRepository
						.pageByBranchWithOrder(branchId, statusBucket, filter.fromInclusive(), filter.toExclusive(),
								gateway, PageRequest.of(0, MAX_FETCH_PER_SOURCE))
						.getContent()));
			}
		}
		if (kw != null) {
			String k = kw;
			merged = merged.stream().filter(r -> matchesKeyword(r, k)).toList();
		}

		BigDecimal totalCompleted = BigDecimal.ZERO;
		BigDecimal totalPending = BigDecimal.ZERO;
		BigDecimal totalCancelled = BigDecimal.ZERO;
		for (TransactionRowDto r : merged) {
			String m = mapUiStatusBucket(r.getSource(), r.getStatus());
			BigDecimal amt = r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO;
			if ("completed".equals(m)) {
				totalCompleted = totalCompleted.add(amt);
			} else if ("pending".equals(m)) {
				totalPending = totalPending.add(amt);
			} else if ("cancelled".equals(m)) {
				totalCancelled = totalCancelled.add(amt);
			}
		}
		long countAll;
		if (kw == null) {
			long cd = branchId == null
					? depositRepository.countAllForTransactionHistory(statusBucket, filter.fromInclusive(),
							filter.toExclusive(), gateway)
					: depositRepository.countForBranchForTransactionHistory(branchId, statusBucket,
							filter.fromInclusive(), filter.toExclusive(), gateway);
			long co = branchId == null
					? orderPaymentRepository.countAllWithFilters(statusBucket, filter.fromInclusive(),
							filter.toExclusive(), gateway)
					: orderPaymentRepository.countByBranchWithFilters(branchId, statusBucket, filter.fromInclusive(),
							filter.toExclusive(), gateway);
			if (!includeDeposit) {
				cd = 0;
			}
			if (!includeOrder) {
				co = 0;
			}
			countAll = cd + co;
		} else {
			countAll = merged.size();
		}

		return TransactionSummaryDto.builder()
				.totalCompleted(totalCompleted)
				.totalPending(totalPending)
				.totalCancelled(totalCancelled)
				.countAll(countAll)
				.build();
	}

	public TransactionDetailDto detail(String source, long id, long actorUserId, boolean isAdmin) {
		if (SOURCE_DEPOSIT.equalsIgnoreCase(source)) {
			Deposit d = depositRepository.findById(id)
					.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đặt cọc."));
			Vehicle v = vehicleRepository.findById(d.getVehicleId()).orElse(null);
			int branchId = v != null && v.getBranch() != null ? v.getBranch().getId() : -1;
			if (!isAdmin) {
				int mine = staffService.getManagerBranchId(actorUserId);
				if (v == null || branchId != mine) {
					throw new BusinessException(ErrorCode.FORBIDDEN, "Không có quyền xem giao dịch này.");
				}
			}
			TransactionRowDto row = toDepositRow(d, loadUserMap(Set.of(d.getCustomerId())),
					loadVehicleMap(Set.of(d.getVehicleId())));
			List<TimelineEventDto> timeline = new ArrayList<>();
			timeline.add(TimelineEventDto.builder()
					.event("Tạo đặt cọc")
					.at(iso(d.getCreatedAt()))
					.detail(null)
					.build());
			if (StringUtils.hasText(d.getPaymentGateway())) {
				timeline.add(TimelineEventDto.builder()
						.event("Bắt đầu thanh toán online")
						.at(iso(d.getCreatedAt()))
						.detail(null)
						.build());
			}
			if ("Confirmed".equals(d.getStatus())) {
				timeline.add(TimelineEventDto.builder()
						.event("Xác nhận thành công")
						.at(null)
						.detail("Đã xác nhận")
						.build());
			}
			if ("Cancelled".equals(d.getStatus())) {
				timeline.add(TimelineEventDto.builder()
						.event("Đã hủy")
						.at(iso(d.getCreatedAt()))
						.detail(d.getNotes())
						.build());
			}
			return TransactionDetailDto.builder()
					.row(row)
					.timeline(timeline)
					.rawGatewayRef(d.getGatewayTxnRef())
					.notes(d.getNotes())
					.build();
		}
		if (SOURCE_ORDER_PAYMENT.equalsIgnoreCase(source)) {
			OrderPayment p = orderPaymentRepository.findByIdWithOrderAndBranch(id)
					.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thanh toán."));
			int branchId = p.getOrder().getBranch().getId();
			if (!isAdmin) {
				int mine = staffService.getManagerBranchId(actorUserId);
				if (branchId != mine) {
					throw new BusinessException(ErrorCode.FORBIDDEN, "Không có quyền xem giao dịch này.");
				}
			}
			TransactionRowDto row = mapOrderPaymentRow(p);
			List<TimelineEventDto> timeline = new ArrayList<>();
			timeline.add(TimelineEventDto.builder()
					.event("Tạo thanh toán")
					.at(iso(p.getCreatedAt()))
					.detail(null)
					.build());
			if (p.getPaidAt() != null) {
				timeline.add(TimelineEventDto.builder()
						.event("Thanh toán thành công")
						.at(iso(p.getPaidAt()))
						.detail(null)
						.build());
			}
			if ("Cancelled".equals(p.getStatus())) {
				timeline.add(TimelineEventDto.builder()
						.event("Đã hủy")
						.at(iso(p.getCreatedAt()))
						.detail(null)
						.build());
			}
			if ("Refunded".equals(p.getStatus())) {
				timeline.add(TimelineEventDto.builder()
						.event("Đã hoàn tiền")
						.at(p.getPaidAt() != null ? iso(p.getPaidAt()) : iso(p.getCreatedAt()))
						.detail(null)
						.build());
			}
			return TransactionDetailDto.builder()
					.row(row)
					.timeline(timeline)
					.rawGatewayRef(p.getTransactionRef())
					.notes(null)
					.build();
		}
		throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Nguồn giao dịch không hợp lệ.");
	}

	private List<TransactionRowDto> listMergedForExport(TransactionFilter filter, long actorUserId, boolean isAdmin) {
		String statusBucket = toStatusBucket(filter.status());
		String gateway = normalizeGatewayParam(filter.gateway());
		String kw = StringUtils.hasText(filter.keyword()) ? filter.keyword().trim().toLowerCase(Locale.ROOT) : null;
		boolean includeDeposit = !StringUtils.hasText(filter.source())
				|| SOURCE_DEPOSIT.equalsIgnoreCase(filter.source());
		boolean includeOrder = !StringUtils.hasText(filter.source())
				|| SOURCE_ORDER_PAYMENT.equalsIgnoreCase(filter.source());
		Integer branchId = resolveBranchIdForQuery(filter.branchId(), actorUserId, isAdmin);
		List<TransactionRowDto> merged = new ArrayList<>();
		if (includeDeposit) {
			if (branchId == null) {
				merged.addAll(mapDeposits(depositRepository
						.pageAllForTransactionHistory(statusBucket, filter.fromInclusive(), filter.toExclusive(),
								gateway, PageRequest.of(0, MAX_FETCH_PER_SOURCE))
						.getContent()));
			} else {
				merged.addAll(mapDeposits(depositRepository
						.pageForBranchForTransactionHistory(branchId, statusBucket, filter.fromInclusive(),
								filter.toExclusive(), gateway, PageRequest.of(0, MAX_FETCH_PER_SOURCE))
						.getContent()));
			}
		}
		if (includeOrder) {
			if (branchId == null) {
				merged.addAll(mapOrderPayments(orderPaymentRepository
						.pageAllWithOrder(statusBucket, filter.fromInclusive(), filter.toExclusive(), gateway,
								PageRequest.of(0, MAX_FETCH_PER_SOURCE))
						.getContent()));
			} else {
				merged.addAll(mapOrderPayments(orderPaymentRepository
						.pageByBranchWithOrder(branchId, statusBucket, filter.fromInclusive(), filter.toExclusive(),
								gateway, PageRequest.of(0, MAX_FETCH_PER_SOURCE))
						.getContent()));
			}
		}
		merged.sort(Comparator.comparing(TransactionRowDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
		if (kw != null) {
			String k = kw;
			merged = merged.stream().filter(r -> matchesKeyword(r, k)).toList();
		}
		if (merged.size() > MAX_EXPORT_ROWS) {
			return merged.subList(0, MAX_EXPORT_ROWS);
		}
		return merged;
	}

	public byte[] exportCsv(TransactionFilter filter, long actorUserId, boolean isAdmin) {
		List<TransactionRowDto> rows = listMergedForExport(filter, actorUserId, isAdmin);
		StringBuilder sb = new StringBuilder();
		sb.append('\uFEFF');
		sb.append("ID,Loại,Nguồn,Số tiền,Trạng thái,Cổng TT,Mã GD,Khách hàng,SĐT,Xe,Chi nhánh,Mã đơn/cọc,Ngày tạo,Ngày TT\n");
		for (TransactionRowDto r : rows) {
			sb.append(csv(r.getId() != null ? String.valueOf(r.getId()) : ""))
					.append(',')
					.append(csv(r.getType()))
					.append(',')
					.append(csv(r.getSource()))
					.append(',')
					.append(csv(r.getAmount() != null ? r.getAmount().toPlainString() : ""))
					.append(',')
					.append(csv(r.getStatusLabel()))
					.append(',')
					.append(csv(r.getPaymentGateway()))
					.append(',')
					.append(csv(r.getGatewayTxnRef()))
					.append(',')
					.append(csv(r.getCustomerName()))
					.append(',')
					.append(csv(r.getCustomerPhone()))
					.append(',')
					.append(csv(r.getVehicleTitle()))
					.append(',')
					.append(csv(r.getBranchName()))
					.append(',')
					.append(csv(orderOrDepositRef(r)))
					.append(',')
					.append(csv(r.getCreatedAt()))
					.append(',')
					.append(csv(r.getPaidAt()))
					.append('\n');
		}
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String orderOrDepositRef(TransactionRowDto r) {
		if (SOURCE_ORDER_PAYMENT.equals(r.getSource())) {
			return r.getOrderId() != null ? r.getOrderId() : "";
		}
		return r.getDepositId() != null ? String.valueOf(r.getDepositId()) : "";
	}

	private static String csv(String s) {
		if (s == null) {
			return "\"\"";
		}
		String t = s.replace("\"", "\"\"");
		return "\"" + t + "\"";
	}

	private Integer resolveBranchIdForQuery(Long branchIdParam, long actorUserId, boolean isAdmin) {
		if (isAdmin) {
			if (branchIdParam == null) {
				return null;
			}
			return staffService.resolveBranchIdForAdminOrBranchStaff(branchIdParam, actorUserId, true);
		}
		return staffService.resolveBranchIdForAdminOrBranchStaff(null, actorUserId, false);
	}

	private static String toStatusBucket(String status) {
		if (!StringUtils.hasText(status)) {
			return null;
		}
		return switch (status.trim().toLowerCase(Locale.ROOT)) {
			case "completed" -> "COMPLETED";
			case "pending" -> "PENDING";
			case "cancelled" -> "CANCELLED";
			default -> null;
		};
	}

	private static String normalizeGatewayParam(String gateway) {
		if (!StringUtils.hasText(gateway)) {
			return null;
		}
		String g = gateway.trim().toLowerCase(Locale.ROOT);
		if ("zalopay".equals(g) || "vnpay".equals(g) || "cash".equals(g)) {
			return g;
		}
		return null;
	}

	private boolean matchesKeyword(TransactionRowDto r, String kw) {
		return contains(r.getCustomerName(), kw) || contains(r.getCustomerPhone(), kw) || contains(r.getGatewayTxnRef(), kw)
				|| contains(r.getOrderId(), kw);
	}

	private static boolean contains(String s, String kw) {
		return s != null && s.toLowerCase(Locale.ROOT).contains(kw);
	}

	private List<TransactionRowDto> mapDeposits(List<Deposit> deposits) {
		if (deposits.isEmpty()) {
			return List.of();
		}
		Set<Long> uids = new HashSet<>();
		Set<Long> vids = new HashSet<>();
		for (Deposit d : deposits) {
			uids.add(d.getCustomerId());
			vids.add(d.getVehicleId());
		}
		Map<Long, User> users = loadUserMap(uids);
		Map<Long, Vehicle> vehicles = loadVehicleMap(vids);
		List<TransactionRowDto> out = new ArrayList<>();
		for (Deposit d : deposits) {
			out.add(toDepositRow(d, users, vehicles));
		}
		return out;
	}

	private TransactionRowDto toDepositRow(Deposit d, Map<Long, User> users, Map<Long, Vehicle> vehicles) {
		User u = users.get(d.getCustomerId());
		Vehicle v = vehicles.get(d.getVehicleId());
		String customerName = u != null ? u.getName() : "Khách vãng lai";
		String phone = u != null ? u.getPhone() : null;
		String gw = normalizeDepositGateway(d);
		return TransactionRowDto.builder()
				.id(d.getId())
				.source(SOURCE_DEPOSIT)
				.sourceId(d.getId())
				.type("Đặt cọc")
				.amount(d.getAmount())
				.status(d.getStatus())
				.statusLabel(depositStatusLabel(d.getStatus()))
				.paymentGateway(gw)
				.gatewayTxnRef(d.getGatewayTxnRef())
				.customerId(d.getCustomerId())
				.customerName(customerName)
				.customerPhone(phone)
				.vehicleId(d.getVehicleId())
				.vehicleTitle(v != null ? v.getTitle() : null)
				.vehicleListingId(v != null ? v.getListingId() : null)
				.branchId(v != null && v.getBranch() != null ? (long) v.getBranch().getId() : null)
				.branchName(v != null && v.getBranch() != null ? v.getBranch().getName() : null)
				.orderId(null)
				.depositId(d.getId())
				.createdAt(iso(d.getCreatedAt()))
				.paidAt(null)
				.build();
	}

	private List<TransactionRowDto> mapOrderPayments(List<OrderPayment> list) {
		List<TransactionRowDto> out = new ArrayList<>();
		Set<Long> uids = new HashSet<>();
		for (OrderPayment p : list) {
			uids.add(p.getOrder().getCustomerId());
		}
		Map<Long, User> users = loadUserMap(uids);
		for (OrderPayment p : list) {
			out.add(mapOrderPaymentRow(p, users));
		}
		return out;
	}

	private TransactionRowDto mapOrderPaymentRow(OrderPayment p) {
		Map<Long, User> users = loadUserMap(Set.of(p.getOrder().getCustomerId()));
		return mapOrderPaymentRow(p, users);
	}

	private TransactionRowDto mapOrderPaymentRow(OrderPayment p, Map<Long, User> users) {
		var o = p.getOrder();
		var v = o.getVehicle();
		var b = o.getBranch();
		User u = users.get(o.getCustomerId());
		String customerName = u != null ? u.getName() : "Khách vãng lai";
		String phone = u != null ? u.getPhone() : null;
		String gw = normalizeOrderGateway(p);
		return TransactionRowDto.builder()
				.id(p.getId())
				.source(SOURCE_ORDER_PAYMENT)
				.sourceId(p.getId())
				.type("Thanh toán đơn")
				.amount(p.getAmount())
				.status(p.getStatus())
				.statusLabel(orderPaymentStatusLabel(p.getStatus()))
				.paymentGateway(gw)
				.gatewayTxnRef(p.getTransactionRef())
				.customerId(o.getCustomerId())
				.customerName(customerName)
				.customerPhone(phone)
				.vehicleId(v != null ? v.getId() : null)
				.vehicleTitle(v != null ? v.getTitle() : null)
				.vehicleListingId(v != null ? v.getListingId() : null)
				.branchId(b != null ? (long) b.getId() : null)
				.branchName(b != null ? b.getName() : null)
				.orderId(o.getOrderNumber())
				.depositId(null)
				.createdAt(iso(p.getCreatedAt()))
				.paidAt(p.getPaidAt() != null ? iso(p.getPaidAt()) : null)
				.build();
	}

	private Map<Long, User> loadUserMap(Set<Long> ids) {
		Map<Long, User> map = new HashMap<>();
		for (Long id : ids) {
			if (id == null) {
				continue;
			}
			userRepository.findByIdAndDeletedFalse(id).ifPresent(u -> map.put(id, u));
		}
		return map;
	}

	private Map<Long, Vehicle> loadVehicleMap(Set<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, Vehicle> map = new HashMap<>();
		for (Vehicle v : vehicleRepository.findAllByIdInWithImages(ids)) {
			map.put(v.getId(), v);
		}
		return map;
	}

	private static String iso(Instant i) {
		return i == null ? null : DateTimeFormatter.ISO_INSTANT.format(i);
	}

	private static String depositStatusLabel(String status) {
		if (status == null) {
			return "";
		}
		return switch (status) {
			case "Confirmed" -> "Đã xác nhận";
			case "Pending" -> "Đang giữ chỗ";
			case "AwaitingPayment" -> "Chờ thanh toán";
			case "Cancelled" -> "Đã hủy";
			default -> status;
		};
	}

	private static String orderPaymentStatusLabel(String status) {
		if (status == null) {
			return "";
		}
		return switch (status) {
			case "Completed" -> "Thành công";
			case "Pending" -> "Chờ thanh toán";
			case "Cancelled" -> "Đã hủy";
			case "Refunded" -> "Đã hoàn tiền";
			default -> status;
		};
	}

	private static String normalizeDepositGateway(Deposit d) {
		if (StringUtils.hasText(d.getPaymentGateway())) {
			return d.getPaymentGateway().trim().toLowerCase(Locale.ROOT);
		}
		String pm = d.getPaymentMethod() != null ? d.getPaymentMethod().trim().toLowerCase(Locale.ROOT) : "";
		if ("zalopay".equals(pm) || "vnpay".equals(pm)) {
			return pm;
		}
		return "cash";
	}

	private static String normalizeOrderGateway(OrderPayment p) {
		String pm = p.getPaymentMethod() != null ? p.getPaymentMethod().trim().toLowerCase(Locale.ROOT) : "";
		if ("zalopay".equals(pm) || "vnpay".equals(pm)) {
			return pm;
		}
		return "cash";
	}

	/** UI bucket: completed | pending | cancelled */
	private static String mapUiStatusBucket(String source, String entityStatus) {
		if (SOURCE_DEPOSIT.equals(source)) {
			if ("Confirmed".equals(entityStatus) || "Pending".equals(entityStatus)) {
				return "completed";
			}
			if ("AwaitingPayment".equals(entityStatus)) {
				return "pending";
			}
			if ("Cancelled".equals(entityStatus)) {
				return "cancelled";
			}
			return "";
		}
		if (SOURCE_ORDER_PAYMENT.equals(source)) {
			if ("Completed".equals(entityStatus)) {
				return "completed";
			}
			if ("Pending".equals(entityStatus)) {
				return "pending";
			}
			if ("Cancelled".equals(entityStatus) || "Refunded".equals(entityStatus)) {
				return "cancelled";
			}
			return "";
		}
		return "";
	}
}
