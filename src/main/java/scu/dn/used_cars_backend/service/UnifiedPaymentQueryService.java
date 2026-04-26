package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.payment.DailyMethodBucketDto;
import scu.dn.used_cars_backend.dto.payment.UnifiedPaymentDashboardDto;
import scu.dn.used_cars_backend.dto.payment.UnifiedPaymentListItemDto;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.OrderPayment;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.OrderPaymentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.service.payment.UnifiedPaymentRowFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnifiedPaymentQueryService {

	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final String ROLE_ADMIN = "ADMIN";
	private static final String ROLE_BRANCH_MANAGER = "BRANCHMANAGER";
	private static final String ROLE_SALES = "SALESSTAFF";

	private final OrderPaymentRepository orderPaymentRepository;
	private final DepositRepository depositRepository;
	private final VehicleRepository vehicleRepository;
	private final UserRepository userRepository;
	private final StaffService staffService;

	@Transactional(readOnly = true)
	public List<UnifiedPaymentListItemDto> page(long actorUserId, String jwtRole, String kind, String paymentMethods,
			String statuses, String keyword, Integer branchIdParam, Long staffUserIdParam, String fromDate, String toDate,
			int page, int size) {
		Instant[] range = resolveRange(fromDate, toDate, false);
		List<UnifiedPaymentListItemDto> merged = loadMerged(actorUserId, jwtRole, branchIdParam, staffUserIdParam,
				range[0], range[1]);
		List<UnifiedPaymentListItemDto> filtered = applyFilters(merged, kind, paymentMethods, statuses, keyword,
				actorUserId, jwtRole, staffUserIdParam);
		filtered.sort(Comparator.comparing(UnifiedPaymentListItemDto::getCreatedAt).reversed());
		int fromIx = Math.max(0, page) * Math.min(Math.max(size, 1), 200);
		int toIx = Math.min(fromIx + Math.min(Math.max(size, 1), 200), filtered.size());
		return filtered.subList(fromIx, toIx);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> pageMeta(long actorUserId, String jwtRole, String kind, String paymentMethods,
			String statuses, String keyword, Integer branchIdParam, Long staffUserIdParam, String fromDate,
			String toDate, int page, int size) {
		Instant[] range = resolveRange(fromDate, toDate, false);
		List<UnifiedPaymentListItemDto> merged = loadMerged(actorUserId, jwtRole, branchIdParam, staffUserIdParam,
				range[0], range[1]);
		List<UnifiedPaymentListItemDto> filtered = applyFilters(merged, kind, paymentMethods, statuses, keyword,
				actorUserId, jwtRole, staffUserIdParam);
		int total = filtered.size();
		int sz = Math.min(Math.max(size, 1), 200);
		int pg = Math.max(0, page);
		int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / sz);
		Map<String, Object> m = new HashMap<>();
		m.put("totalElements", (long) total);
		m.put("totalPages", totalPages);
		m.put("page", pg);
		m.put("size", sz);
		return m;
	}

	@Transactional(readOnly = true)
	public UnifiedPaymentDashboardDto dashboard(long actorUserId, String jwtRole, Integer branchIdParam,
			Long staffUserIdParam, String fromDate, String toDate) {
		Instant[] range = resolveRange(fromDate, toDate, true);
		List<UnifiedPaymentListItemDto> merged = loadMerged(actorUserId, jwtRole, branchIdParam, staffUserIdParam,
				range[0], range[1]);
		List<UnifiedPaymentListItemDto> filtered = applyFilters(merged, null, null, null, null, actorUserId, jwtRole,
				staffUserIdParam);
		BigDecimal totalSuccess = BigDecimal.ZERO;
		long ok = 0;
		long pend = 0;
		long bad = 0;
		Map<String, long[]> daily = new HashMap<>();
		LocalDate startChart = LocalDate.now(VN).minusDays(29);
		for (UnifiedPaymentListItemDto r : filtered) {
			String g = r.getGatewayStatusCode();
			if ("SUCCESS".equals(g)) {
				ok++;
				totalSuccess = totalSuccess.add(new BigDecimal(r.getAmount()));
			}
			else if ("PROCESSING".equals(g) || "NOT_STARTED".equals(g)) {
				pend++;
			}
			else if ("FAILED".equals(g)) {
				bad++;
			}
			LocalDate day = Instant.parse(r.getCreatedAt()).atZone(VN).toLocalDate();
			if (!day.isBefore(startChart)) {
				String key = day.format(DateTimeFormatter.ISO_LOCAL_DATE);
				long[] bucket = daily.computeIfAbsent(key, k -> new long[3]);
				long amt = parseAmountLong(r.getAmount());
				String pm = r.getPaymentMethod().toLowerCase(Locale.ROOT);
				if (pm.contains("vnpay")) {
					bucket[0] += amt;
				}
				else if (pm.contains("zalo")) {
					bucket[1] += amt;
				}
				else {
					bucket[2] += amt;
				}
			}
		}
		List<DailyMethodBucketDto> series = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			LocalDate d = startChart.plusDays(i);
			String key = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
			long[] b = daily.getOrDefault(key, new long[3]);
			series.add(DailyMethodBucketDto.builder()
					.day(key)
					.vnpayAmount(b[0])
					.zalopayAmount(b[1])
					.cashAmount(b[2])
					.build());
		}
		return UnifiedPaymentDashboardDto.builder()
				.totalAmountInPeriod(totalSuccess.toPlainString())
				.successCount(ok)
				.pendingCount(pend)
				.failedOrCancelledCount(bad)
				.last30DaysByMethod(series)
				.build();
	}

	@Transactional(readOnly = true)
	public List<UnifiedPaymentListItemDto> exportRows(long actorUserId, String jwtRole, String kind,
			String paymentMethods, String statuses, String keyword, Integer branchIdParam, Long staffUserIdParam,
			String fromDate, String toDate) {
		Instant[] range = resolveRange(fromDate, toDate, false);
		List<UnifiedPaymentListItemDto> merged = loadMerged(actorUserId, jwtRole, branchIdParam, staffUserIdParam,
				range[0], range[1]);
		List<UnifiedPaymentListItemDto> filtered = applyFilters(merged, kind, paymentMethods, statuses, keyword,
				actorUserId, jwtRole, staffUserIdParam);
		filtered.sort(Comparator.comparing(UnifiedPaymentListItemDto::getCreatedAt).reversed());
		return filtered;
	}

	private static long parseAmountLong(String amount) {
		try {
			return new BigDecimal(amount).longValue();
		}
		catch (Exception e) {
			return 0L;
		}
	}

	private List<UnifiedPaymentListItemDto> loadMerged(long actorUserId, String jwtRole, Integer branchIdParam,
			Long staffUserIdParam, Instant from, Instant to) {
		Collection<Integer> branchIds = resolveBranchIds(actorUserId, jwtRole, branchIdParam);
		List<OrderPayment> ops;
		List<Deposit> deps;
		if (branchIds == null) {
			ops = orderPaymentRepository.listForUnifiedByCreatedAtRange(from, to);
			deps = depositRepository.listForUnifiedByCreatedAtRange(from, to);
		}
		else {
			ops = orderPaymentRepository.listForUnifiedByCreatedAtRangeAndBranches(from, to, branchIds);
			deps = depositRepository.listForUnifiedByCreatedAtRangeAndBranches(from, to, branchIds);
		}
		Set<Long> userIds = new HashSet<>();
		for (OrderPayment p : ops) {
			userIds.add(p.getOrder().getCustomerId());
			if (p.getOrder().getStaffId() != null) {
				userIds.add(p.getOrder().getStaffId());
			}
		}
		Set<Long> vehicleIds = new HashSet<>();
		for (Deposit d : deps) {
			userIds.add(d.getCustomerId());
			if (d.getCreatedBy() != null) {
				userIds.add(d.getCreatedBy());
			}
			vehicleIds.add(d.getVehicleId());
		}
		Map<Long, User> usersById = new HashMap<>();
		for (User u : userRepository.findAllById(userIds)) {
			usersById.put(u.getId(), u);
		}
		Map<Long, Vehicle> vehiclesById = vehicleIds.isEmpty() ? Map.of()
				: vehicleRepository.findAllByIdInWithBranch(vehicleIds).stream()
						.collect(Collectors.toMap(Vehicle::getId, v -> v));
		List<UnifiedPaymentListItemDto> out = new ArrayList<>();
		for (OrderPayment p : ops) {
			out.add(UnifiedPaymentRowFactory.fromOrderPayment(p, usersById));
		}
		for (Deposit d : deps) {
			out.add(UnifiedPaymentRowFactory.fromDeposit(d, usersById, vehiclesById));
		}
		Long staffFilter = resolveStaffFilter(actorUserId, jwtRole, staffUserIdParam);
		if (staffFilter != null) {
			final long sf = staffFilter;
			out.removeIf(r -> r.getStaffUserId() == null || r.getStaffUserId() != sf);
		}
		return out;
	}

	private Long resolveStaffFilter(long actorUserId, String jwtRole, Long staffUserIdParam) {
		if (ROLE_SALES.equals(jwtRole)) {
			return actorUserId;
		}
		if (staffUserIdParam != null && (ROLE_ADMIN.equals(jwtRole) || ROLE_BRANCH_MANAGER.equals(jwtRole))) {
			return staffUserIdParam;
		}
		return null;
	}

	private Collection<Integer> resolveBranchIds(long actorUserId, String jwtRole, Integer branchIdParam) {
		if (ROLE_ADMIN.equals(jwtRole)) {
			if (branchIdParam == null) {
				return null;
			}
			return List.of(branchIdParam);
		}
		if (ROLE_BRANCH_MANAGER.equals(jwtRole) || ROLE_SALES.equals(jwtRole)) {
			return List.of(staffService.getManagerBranchId(actorUserId));
		}
		throw new BusinessException(ErrorCode.FORBIDDEN, "Không có quyền xem giao dịch thống nhất.");
	}

	private List<UnifiedPaymentListItemDto> applyFilters(List<UnifiedPaymentListItemDto> rows, String kind,
			String paymentMethods, String statuses, String keyword, long actorUserId, String jwtRole,
			Long staffUserIdParam) {
		String k = kind != null ? kind.trim().toUpperCase(Locale.ROOT) : "";
		Set<String> methodSet = parseLowerSet(paymentMethods);
		Set<String> statusSet = parseUpperStatusSet(statuses);
		String kw = keyword != null ? keyword.trim().toLowerCase(Locale.ROOT) : "";
		return rows.stream().filter(r -> {
			if (!k.isEmpty()) {
				if ("DEPOSIT".equals(k) && !"DEPOSIT".equals(r.getKind())) {
					return false;
				}
				if ("ORDER_PAYMENT".equals(k) && !"ORDER_PAYMENT".equals(r.getKind())) {
					return false;
				}
			}
			if (!methodSet.isEmpty()) {
				String pm = r.getPaymentMethod().toLowerCase(Locale.ROOT);
				boolean hit = false;
				for (String m : methodSet) {
					if (m.contains("vnpay") && pm.contains("vnpay")) {
						hit = true;
					}
					if (m.contains("zalo") && pm.contains("zalo")) {
						hit = true;
					}
					if ((m.contains("cash") || m.contains("tien") || m.contains("mặt")) && !pm.contains("vnpay")
							&& !pm.contains("zalo")) {
						hit = true;
					}
				}
				if (!hit) {
					return false;
				}
			}
			if (!statusSet.isEmpty()) {
				String bs = r.getBusinessStatus() != null ? r.getBusinessStatus() : "";
				if (statusSet.stream().noneMatch(s -> s.equalsIgnoreCase(bs))) {
					return false;
				}
			}
			if (!kw.isEmpty()) {
				String blob = (r.getTxnRefDisplay() + " " + r.getCustomerName() + " " + r.getCustomerPhone() + " "
						+ r.getVehicleTitle() + " " + r.getListingId()).toLowerCase(Locale.ROOT);
				if (!blob.contains(kw)) {
					return false;
				}
			}
			return true;
		}).collect(Collectors.toList());
	}

	private static Set<String> parseLowerSet(String csv) {
		if (csv == null || csv.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(csv.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
	}

	private static Set<String> parseUpperStatusSet(String csv) {
		if (csv == null || csv.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
	}

	private Instant[] resolveRange(String fromDate, String toDate, boolean dashboardDefault30) {
		LocalDate to = LocalDate.now(VN);
		LocalDate from = dashboardDefault30 ? to.minusDays(29) : to.minusDays(29);
		if (fromDate != null && !fromDate.isBlank()) {
			try {
				from = LocalDate.parse(fromDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
			}
			catch (DateTimeParseException e) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "fromDate không hợp lệ.");
			}
		}
		else if (!dashboardDefault30) {
			from = to.minusDays(29);
		}
		if (toDate != null && !toDate.isBlank()) {
			try {
				to = LocalDate.parse(toDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
			}
			catch (DateTimeParseException e) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "toDate không hợp lệ.");
			}
		}
		if (from.isAfter(to)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "fromDate không được sau toDate.");
		}
		Instant start = from.atStartOfDay(VN).toInstant();
		Instant end = to.plusDays(1).atStartOfDay(VN).toInstant();
		return new Instant[] { start, end };
	}
}
