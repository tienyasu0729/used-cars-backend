package scu.dn.used_cars_backend.service;

// Service xá»­ lÃ½ logic xe: danh sÃ¡ch/chi tiáº¿t cÃ´ng khai, lÆ°u xe cho khÃ¡ch, táº¡o-sá»­a-xÃ³a cho manager.
// Map DTO thá»§ cÃ´ng trong service (khÃ´ng dÃ¹ng MapStruct). Cache Ä‘á»c/ghi báº±ng CacheManager cho dá»… hiá»ƒu.

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleCreateRequest;
import scu.dn.used_cars_backend.dto.vehicle.VehicleDetailDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleImageDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleImageWriteDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleListingFacetsDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleListResponse;
import scu.dn.used_cars_backend.dto.vehicle.VehicleSummaryDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleUpdateRequest;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.Category;
import scu.dn.used_cars_backend.entity.StaffAssignment;
import scu.dn.used_cars_backend.entity.Subcategory;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleImage;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.CategoryRepository;
import scu.dn.used_cars_backend.repository.InstallmentApplicationRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.SubcategoryRepository;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.VehicleImageRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scu.dn.used_cars_backend.dto.vehicle.SuggestionDto;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class VehicleService {

	private static final Set<String> VEHICLE_STATUSES = Set.of("Available", "Reserved", "Sold", "Hidden", "InTransfer");

	// State machine chuyen trang thai xe (chi Staff/Manager tuan theo;
	// Admin override duoc nhung BAT BUOC co note).
	// - Sold la trang thai cuoi: khong tu dong chuyen tay ve Available/Reserved.
	// - Reserved co the ve Available (huy don) hoac Sold (confirm ban).
	private static final Map<String, Set<String>> ALLOWED_VEHICLE_TRANSITIONS = Map.of(
			"Available", Set.of("Reserved", "Hidden", "InTransfer"),
			"Reserved", Set.of("Available", "Sold"),
			"Sold", Set.of(),
			"Hidden", Set.of("Available"),
			"InTransfer", Set.of("Available"));

	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
	/** Tiá»n tá»‘ khÃ³a cache â€” Ä‘á»•i khi DTO list/detail thay Ä‘á»•i Ä‘á»ƒ trÃ¡nh tráº£ báº£n cÅ© thiáº¿u field. */
	private static final String VEHICLE_LIST_CACHE_PREFIX = "v4:";
	private static final String VEHICLE_DETAIL_CACHE_PREFIX = "v4:";
	/** MÃ£ tin (listing_id): chuá»—i sá»‘ ngáº«u nhiÃªn, khÃ´ng pháº£i khÃ³a chÃ­nh; cá»™t DB unique, Ä‘á»™ dÃ i tá»‘i Ä‘a 20. */
	private static final int LISTING_ID_DIGITS = 12;
	private static final int LISTING_ID_MAX_ATTEMPTS = 20;

	private static final Logger log = LoggerFactory.getLogger(VehicleService.class);

	private final VehicleRepository vehicleRepository;
	private final VehicleImageRepository vehicleImageRepository;
	private final CategoryRepository categoryRepository;
	private final SubcategoryRepository subcategoryRepository;
	private final BranchRepository branchRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final CacheManager cacheManager;
	private final DepositService depositService;
	private final DepositRepository depositRepository;
	private final InstallmentApplicationRepository installmentApplicationRepository;
	private final EmailNotificationService emailNotificationService;
	private final SecureRandom listingIdRandom = new SecureRandom();

	public VehicleService(VehicleRepository vehicleRepository, VehicleImageRepository vehicleImageRepository,
			CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository,
			BranchRepository branchRepository, StaffAssignmentRepository staffAssignmentRepository,
			CacheManager cacheManager, @Lazy DepositService depositService, DepositRepository depositRepository,
			InstallmentApplicationRepository installmentApplicationRepository,
			EmailNotificationService emailNotificationService) {
		this.vehicleRepository = vehicleRepository;
		this.vehicleImageRepository = vehicleImageRepository;
		this.categoryRepository = categoryRepository;
		this.subcategoryRepository = subcategoryRepository;
		this.branchRepository = branchRepository;
		this.staffAssignmentRepository = staffAssignmentRepository;
		this.cacheManager = cacheManager;
		this.depositService = depositService;
		this.depositRepository = depositRepository;
		this.installmentApplicationRepository = installmentApplicationRepository;
		this.emailNotificationService = emailNotificationService;
	}

	@Transactional(readOnly = true)
	public VehicleListResponse listPublic(Integer categoryId, Integer subcategoryId, BigDecimal minPrice,
			BigDecimal maxPrice, Integer yearMin, Integer yearMax, String transmission, Integer branchId, int page,
			int size, String sort, String keyword) {
		String tx = transmission != null && !transmission.isBlank() ? transmission.trim() : null;
		String kw = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
		String sortKey = normalizeListSortKey(sort);

		// Khi cÃ³ keyword thÃ¬ bá» cache (keyword ráº¥t Ä‘a dáº¡ng, cache key sáº½ phÃ¬nh to)
		if (kw != null) {
			return loadListFromDatabase(kw, categoryId, subcategoryId, minPrice, maxPrice, yearMin, yearMax,
					tx, branchId, page, size, sortKey);
		}

		String key = buildListCacheKey(categoryId, subcategoryId, minPrice, maxPrice, yearMin, yearMax, tx, branchId,
				page, size, sortKey);
		Cache cache = cacheManager.getCache("vehicleList");
		if (cache != null) {
			Cache.ValueWrapper w = cache.get(key);
			if (w != null && w.get() != null) {
				return (VehicleListResponse) w.get();
			}
		}
		VehicleListResponse body = loadListFromDatabase(null, categoryId, subcategoryId, minPrice, maxPrice, yearMin,
				yearMax, tx, branchId, page, size, sortKey);
		if (cache != null) {
			cache.put(key, body);
		}
		return body;
	}

	/**
	 * So sÃ¡nh cÃ´ng khai: 2â€“3 xe, cÃ¹ng Ä‘iá»u kiá»‡n hiá»ƒn thá»‹ nhÆ° chi tiáº¿t cÃ´ng khai.
	 */
	@Transactional(readOnly = true)
	public java.util.List<VehicleDetailDto> comparePublic(java.util.List<Long> ids) {
		if (ids == null || ids.size() < 2 || ids.size() > 3) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chá»‰ so sÃ¡nh tá»« 2 Ä‘áº¿n 3 xe.");
		}
		long distinct = ids.stream().distinct().count();
		if (distinct != ids.size()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sÃ¡ch xe khÃ´ng Ä‘Æ°á»£c trÃ¹ng láº·p.");
		}
		java.util.List<Vehicle> found = vehicleRepository.findPublicByIds(ids);
		java.util.Map<Long, Vehicle> byId = new java.util.HashMap<>();
		for (Vehicle v : found) {
			byId.put(v.getId(), v);
		}
		java.util.List<VehicleDetailDto> out = new java.util.ArrayList<>();
		for (Long id : ids) {
			Vehicle v = byId.get(id);
			if (v == null) {
				throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y xe.");
			}
			out.add(toPublicDetailDto(v));
		}
		return out;
	}

	@Transactional(readOnly = true)
	public VehicleDetailDto getPublicDetail(long id) {
		// B1: thá»­ láº¥y tá»« cache chi tiáº¿t
		String key = detailCacheKey(id);
		Cache cache = cacheManager.getCache("vehicleDetail");
		if (cache != null) {
			Cache.ValueWrapper w = cache.get(key);
			if (w != null && w.get() != null) {
				return (VehicleDetailDto) w.get();
			}
		}
		// B2: DB; khÃ´ng tÃ¬m tháº¥y thÃ¬ khÃ´ng put cache (giá»‘ng unless = #result == null)
		VehicleDetailDto dto = vehicleRepository.findPublicDetailById(id).map(this::toPublicDetailDto).orElse(null);
		if (dto != null && cache != null) {
			cache.put(key, dto);
		}
		return dto;
	}

	/**
	 * Resolve chi tiáº¿t xe tá»« token public. Æ¯u tiÃªn id ná»™i bá»™; fallback listingId Ä‘á»ƒ khÃ´ng gÃ£y link cÅ© Ä‘Ã£ phÃ¡t tÃ¡n.
	 */
	@Transactional(readOnly = true)
	public VehicleDetailDto getPublicDetailByToken(String token) {
		String normalized = token != null ? token.trim() : "";
		if (normalized.isEmpty()) {
			return null;
		}
		Long numericId = parsePositiveLong(normalized);
		if (numericId != null) {
			VehicleDetailDto byId = getPublicDetail(numericId);
			if (byId != null) {
				return byId;
			}
		}
		return vehicleRepository.findPublicDetailByListingId(normalized)
				.map(this::toPublicDetailDto)
				.orElse(null);
	}

	/**
	 * Chi tiáº¿t xe cÃ´ng khai + thÃªm myPendingDepositId / myConfirmedDepositId cho user Ä‘Ã£ login.
	 * Reuse DTO tá»« cache, chá»‰ enrich thÃªm field user-specific.
	 */
	@Transactional(readOnly = true)
	public VehicleDetailDto getPublicDetailForUser(long vehicleId, Long userId) {
		return getPublicDetailForUserByToken(String.valueOf(vehicleId), userId);
	}

	@Transactional(readOnly = true)
	public VehicleDetailDto getPublicDetailForUserByToken(String token, Long userId) {
		VehicleDetailDto cached = getPublicDetailByToken(token);
		if (cached == null && userId != null) {
			Vehicle managedVehicle = findOwnedManagedVehicleByToken(token, userId);
			if (managedVehicle != null) {
				cached = toPublicDetailDto(managedVehicle);
			}
		}
		if (cached == null) {
			return null;
		}
		VehicleDetailDto dto = new VehicleDetailDto();
		BeanUtils.copyProperties(cached, dto);
		dto.setMyPendingDepositId(null);
		dto.setMyConfirmedDepositId(null);
		if (userId != null && dto.getId() != null) {
			var deposits = depositRepository.findByVehicleIdAndStatusIn(
					dto.getId(), List.of("AwaitingPayment", "Confirmed"));
			for (var d : deposits) {
				if (d.getCustomerId() != userId) continue;
				if ("AwaitingPayment".equals(d.getStatus())) {
					dto.setMyPendingDepositId(d.getId());
				} else if ("Confirmed".equals(d.getStatus())) {
					dto.setMyConfirmedDepositId(d.getId());
				}
			}
		}
		return dto;
	}

	private Vehicle findOwnedManagedVehicleByToken(String token, Long userId) {
		String normalized = token != null ? token.trim() : "";
		if (normalized.isEmpty()) {
			return null;
		}
		Long numericId = parsePositiveLong(normalized);
		if (numericId == null) {
			return null;
		}
		return vehicleRepository.findManagedDetailById(numericId)
				.filter(vehicle -> hasVehicleOwnershipContext(userId, vehicle.getId()))
				.orElse(null);
	}

	private boolean hasVehicleOwnershipContext(Long userId, Long vehicleId) {
		if (userId == null || vehicleId == null) {
			return false;
		}
		if (depositRepository.existsByCustomerIdAndVehicleId(userId, vehicleId)) {
			return true;
		}
		return installmentApplicationRepository.existsByCustomer_IdAndVehicle_Id(userId, vehicleId);
	}

	/**
	 * Danh sÃ¡ch xe trong pháº¡m vi quáº£n lÃ½: Admin xem má»i chi nhÃ¡nh; manager/staff chá»‰ chi nhÃ¡nh
	 * (manager_id hoáº·c StaffAssignments active). KhÃ¡c GET /vehicles cÃ´ng khai: gá»“m cáº£ xe Ä‘Ã£ áº©n (deleted/Hidden).
	 * scope=NETWORK: toÃ n há»‡ thá»‘ng nhÆ°ng chá»‰ xe cÃ²n hiá»ƒn thá»‹ cÃ´ng khai (deleted=false, status khÃ¡c Hidden) â€” tra cá»©u Ä‘iá»u chuyá»ƒn.
	 */
	public VehicleListResponse listForManager(long actorUserId, boolean isAdmin, Integer categoryId,
			Integer subcategoryId, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, Integer yearMin,
			Integer yearMax, String transmission, Integer branchId, int page, int size, String sort, String scope,
			String vehicleStatusFilter, String excludeStatusFilter, String keyword) {
		String tx = transmission != null && !transmission.isBlank() ? transmission.trim() : null;
		String vs = vehicleStatusFilter != null && !vehicleStatusFilter.isBlank()
				&& !"all".equalsIgnoreCase(vehicleStatusFilter.trim())
						? vehicleStatusFilter.trim()
						: null;
		String excludeStatus = excludeStatusFilter != null && !excludeStatusFilter.isBlank()
				&& !"all".equalsIgnoreCase(excludeStatusFilter.trim())
						? excludeStatusFilter.trim()
						: null;
		String kw = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
		String sortKey = normalizeListSortKey(sort);
		Sort sortObj = listSortForPublicList(sortKey);
		int pg = Math.max(0, page);
		int normalizedSize = Math.min(100, Math.max(1, size));
		boolean networkScope = "NETWORK".equalsIgnoreCase(scope);
		String cacheKey = buildManagerListCacheKey(actorUserId, isAdmin, categoryId, subcategoryId, minPrice, maxPrice,
				yearMin, yearMax, tx, branchId, pg, normalizedSize, sortKey, scope, vs, excludeStatus);
		Cache listCache = cacheManager.getCache("vehicleList");

		if (kw == null && listCache != null) {
			VehicleListResponse cached = listCache.get(cacheKey, VehicleListResponse.class);
			if (cached != null) {
				return cached;
			}
		}

		// Tab tra cá»©u máº¡ng lÆ°á»›i: cÃ¹ng Ä‘iá»u kiá»‡n list cÃ´ng khai, má»i chi nhÃ¡nh (cÃ³ thá»ƒ lá»c branchId)
		if (networkScope) {
			PageRequest pr = PageRequest.of(pg, normalizedSize, sortObj);
			Page<Vehicle> p = vehicleRepository.findPublicPage(kw, categoryId, subcategoryId, minPrice, maxPrice,
					yearMin, yearMax, tx, branchId, pr);
			VehicleListResponse res = buildManagerListResponse(p);
			if (kw == null && listCache != null) {
				listCache.put(cacheKey, res);
			}
			return res;
		}

		PageRequest pr = PageRequest.of(pg, normalizedSize, sortObj);

		List<Integer> branchIds;
		if (isAdmin) {
			branchIds = branchRepository.findAllByDeletedFalseOrderByIdAsc().stream().map(Branch::getId).toList();
		} else {
			branchIds = resolveManageableBranchIds(actorUserId);
		}
		if (branchIds.isEmpty()) {
			return emptyListResponse(pg, normalizedSize);
		}
		if (branchId != null && !branchIds.contains(branchId)) {
			return emptyListResponse(pg, normalizedSize);
		}

		Page<Vehicle> p = vehicleRepository.findManagedPage(branchIds, kw, categoryId, subcategoryId, minPrice, maxPrice,
				yearMin, yearMax, tx, branchId, vs, excludeStatus, pr);
		VehicleListResponse res = buildManagerListResponse(p);
		if (kw == null && listCache != null) {
			listCache.put(cacheKey, res);
		}
		return res;
	}

	@Transactional(readOnly = true)
	public VehicleListingFacetsDto getPublicListingFacets() {
		Cache facetsCache = cacheManager.getCache("vehicleFacets");
		String cacheKey = "public:v2";
		if (facetsCache != null) {
			VehicleListingFacetsDto cached = facetsCache.get(cacheKey, VehicleListingFacetsDto.class);
			if (cached != null) {
				return cached;
			}
		}

		VehicleListingFacetsDto dto = new VehicleListingFacetsDto();
		dto.setCategoryIds(vehicleRepository.findPublicCategoryIds());

		Map<Integer, List<Integer>> subcategoryMap = new HashMap<>();
		for (Object[] pair : vehicleRepository.findPublicCategorySubcategoryPairs()) {
			if (pair == null || pair.length < 2 || pair[0] == null || pair[1] == null) {
				continue;
			}
			Integer categoryId = ((Number) pair[0]).intValue();
			Integer subcategoryId = ((Number) pair[1]).intValue();
			subcategoryMap.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(subcategoryId);
		}
		dto.setSubcategoryIdsByCategory(subcategoryMap);

		Object[] priceRange = vehicleRepository.findPublicPriceRange();
		BigDecimal priceMin = priceRange != null && priceRange.length >= 2 && priceRange[0] instanceof BigDecimal bd
				? bd
				: null;
		BigDecimal priceMax = priceRange != null && priceRange.length >= 2 && priceRange[1] instanceof BigDecimal bd
				? bd
				: null;
		if (priceMin == null || priceMax == null) {
			Page<Vehicle> minPage = vehicleRepository.findPublicPage(null, null, null, null, null, null, null, null, null,
					PageRequest.of(0, 1, Sort.by(Order.asc("price").with(Sort.NullHandling.NULLS_LAST), Order.desc("id"))));
			if (!minPage.isEmpty()) {
				priceMin = minPage.getContent().get(0).getPrice();
			}
			Page<Vehicle> maxPage = vehicleRepository.findPublicPage(null, null, null, null, null, null, null, null, null,
					PageRequest.of(0, 1, Sort.by(Order.desc("price").with(Sort.NullHandling.NULLS_LAST), Order.desc("id"))));
			if (!maxPage.isEmpty()) {
				priceMax = maxPage.getContent().get(0).getPrice();
			}
		}
		dto.setPriceMin(priceMin);
		dto.setPriceMax(priceMax);

		if (facetsCache != null) {
			facetsCache.put(cacheKey, dto);
		}
		return dto;
	}

	/** Chi tiáº¿t xe cho mÃ n sá»­a manager â€” 403 náº¿u xe khÃ´ng thuá»™c chi nhÃ¡nh Ä‘Æ°á»£c quáº£n lÃ½. */
	@Transactional(readOnly = true)
	public VehicleDetailDto getManagedDetail(long id, long actorUserId, boolean isAdmin) {
		Vehicle v = vehicleRepository.findManagedDetailById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		return toManagedDetailDto(v);
	}

	@Transactional
	public VehicleDetailDto createVehicle(VehicleCreateRequest req, long actorUserId, boolean isAdmin) {
		// B1: khÃ³a category + load subcategory + branch vÃ  kiá»ƒm tra quyá»n
		Category category = categoryRepository.findByIdForUpdate(req.getCategoryId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y hÃ£ng."));
		Subcategory sub = loadSubcategoryForCategory(req.getSubcategoryId(), req.getCategoryId());
		Branch branch = loadBranchAndAssertManager(req.getBranchId(), actorUserId, isAdmin);

		// B2: táº¡o entity + listing_id (sá»‘ ngáº«u nhiÃªn duy nháº¥t, khÃ¡c id khÃ³a chÃ­nh) + áº£nh
		Vehicle v = new Vehicle();
		v.setListingId(nextRandomUniqueListingId());
		v.setCategory(category);
		v.setSubcategory(sub);
		v.setBranch(branch);
		copyCreateRequestToVehicle(req, v, actorUserId);
		applyImagesFromRequest(v, req.getImages());

		Vehicle saved = vehicleRepository.save(v);
		evictVehicleCaches(saved.getId());

		// Gá»­i email thÃ´ng bÃ¡o xe má»›i (async, khÃ´ng áº£nh hÆ°á»Ÿng luá»“ng táº¡o xe)
		try {
			emailNotificationService.sendNewVehicleNotificationAsync(saved);
		} catch (Exception e) {
			log.warn("KhÃ´ng gá»­i Ä‘Æ°á»£c email thÃ´ng bÃ¡o xe má»›i: {}", e.getMessage());
		}

		return toManagedDetailDto(saved);
	}

	@Transactional
	public VehicleDetailDto updateVehicle(long id, VehicleUpdateRequest req, long actorUserId, boolean isAdmin) {
		// B1: láº¥y xe + quyá»n trÃªn chi nhÃ¡nh (ká»ƒ cáº£ xe Ä‘Ã£ áº©n khá»i cÃ´ng khai)
		Vehicle v = vehicleRepository.findManagedDetailById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());

		// B2: load ref má»›i + validate status
		Category category = categoryRepository.findById(req.getCategoryId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y hÃ£ng."));
		Subcategory sub = loadSubcategoryForCategory(req.getSubcategoryId(), req.getCategoryId());
		Branch branch = loadBranchAndAssertManager(req.getBranchId(), actorUserId, isAdmin);
		if (!VEHICLE_STATUSES.contains(req.getStatus())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tráº¡ng thÃ¡i xe khÃ´ng há»£p lá»‡.");
		}

		// B3: ghi Ä‘Ã¨ field + thay áº£nh
		boolean wasDeleted = v.isDeleted();
		copyUpdateRequestToVehicle(req, v, category, sub, branch);
		// KhÃ´i phá»¥c hiá»ƒn thá»‹ cÃ´ng khai khi khÃ´ng cÃ²n tráº¡ng thÃ¡i Hidden (Hidden váº«n áº©n list cÃ´ng khai)
		if (wasDeleted && !"Hidden".equals(req.getStatus())) {
			v.setDeleted(false);
		}
		v.getImages().clear();
		applyImagesFromRequest(v, req.getImages());

		if ("Available".equals(req.getStatus())) {
			depositService.syncOpenDepositsWhenVehicleSetAvailable(id);
		}

		Vehicle saved = vehicleRepository.save(v);
		evictVehicleCaches(saved.getId());
		return toManagedDetailDto(saved);
	}

	// Duyá»‡t Ä‘iá»u chuyá»ƒn: Ä‘Ã¡nh dáº¥u xe InTransfer táº¡i chi nhÃ¡nh nguá»“n.
	// DÃ¹ng findById thay vÃ¬ findManagedDetailById Ä‘á»ƒ trÃ¡nh INNER JOIN qua @EntityGraph
	// gÃ¢y máº¥t káº¿t quáº£ khi quan há»‡ bá»‹ lá»—i dá»¯ liá»‡u. KhÃ´ng cháº·n xe bá»‹ áº©n (is_deleted)
	// vÃ¬ Ä‘iá»u chuyá»ƒn lÃ  nghiá»‡p vá»¥ ná»™i bá»™, khÃ´ng phá»¥ thuá»™c tráº¡ng thÃ¡i hiá»ƒn thá»‹ cÃ´ng khai.
	@Transactional
	public void applyTransferApprovedMarkInTransfer(long vehicleId, int expectedFromBranchId) {
		Vehicle v = vehicleRepository.findById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y xe."));
		if (v.getBranch().getId() != expectedFromBranchId) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_IN_BRANCH, "Xe khÃ´ng thuá»™c chi nhÃ¡nh nguá»“n cá»§a yÃªu cáº§u.");
		}
		if (!"Available".equals(v.getStatus())) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Xe khÃ´ng á»Ÿ tráº¡ng thÃ¡i Available Ä‘á»ƒ duyá»‡t Ä‘iá»u chuyá»ƒn.");
		}
		v.setStatus("InTransfer");
		vehicleRepository.save(v);
		evictVehicleCaches(vehicleId);
	}

	// HoÃ n táº¥t Ä‘iá»u chuyá»ƒn: chuyá»ƒn branch + Ä‘áº·t Available.
	// TÆ°Æ¡ng tá»± dÃ¹ng findById vÃ  khÃ´ng cháº·n is_deleted.
	// Khi hoÃ n táº¥t, bá» cá» áº©n (deleted=false) Ä‘á»ƒ xe hiá»‡n láº¡i á»Ÿ chi nhÃ¡nh má»›i.
	@Transactional
	public void applyTransferCompleteMoveToBranch(long vehicleId, int fromBranchId, int toBranchId) {
		Vehicle v = vehicleRepository.findById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y xe."));
		if (!"InTransfer".equals(v.getStatus())) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Xe khÃ´ng á»Ÿ tráº¡ng thÃ¡i InTransfer Ä‘á»ƒ hoÃ n táº¥t Ä‘iá»u chuyá»ƒn.");
		}
		if (v.getBranch().getId() != fromBranchId) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_IN_BRANCH, "Xe khÃ´ng cÃ²n á»Ÿ chi nhÃ¡nh nguá»“n cá»§a yÃªu cáº§u.");
		}
		Branch to = branchRepository.findByIdAndDeletedFalse(toBranchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y chi nhÃ¡nh Ä‘Ã­ch."));
		v.setBranch(to);
		v.setDeleted(false);
		depositService.syncOpenDepositsWhenVehicleSetAvailable(vehicleId);
		v.setStatus("Available");
		vehicleRepository.save(v);
		evictVehicleCaches(vehicleId);
	}

	@Transactional
	public void softDeleteVehicle(long id, long actorUserId, boolean isAdmin) {
		Vehicle v = vehicleRepository.findManagedDetailById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y xe."));
		if (v.isDeleted()) {
			return;
		}
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		v.setDeleted(true);
		vehicleRepository.save(v);
		evictVehicleCaches(id);
	}

	/** Bá» cá» xÃ³a má»m â€” xe hiá»‡n láº¡i trÃªn list cÃ´ng khai (trá»« khi status = Hidden). */
	@Transactional
	public VehicleDetailDto restorePublicListing(long id, long actorUserId, boolean isAdmin) {
		Vehicle v = vehicleRepository.findManagedDetailById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		if (!v.isDeleted()) {
			return toManagedDetailDto(v);
		}
		v.setDeleted(false);
		vehicleRepository.save(v);
		evictVehicleCaches(id);
		return toManagedDetailDto(v);
	}

	// ===================== SPRINT 4 â€” STATUS + BULK + IMAGE =====================

	/** Äá»•i tráº¡ng thÃ¡i xe Ä‘Æ¡n láº» (Available, Reserved, Sold, Hidden). */
	@Transactional
	public VehicleDetailDto changeVehicleStatus(long vehicleId, String newStatus, String note,
			long actorUserId, boolean isAdmin) {
		// B1: kiá»ƒm tra tráº¡ng thÃ¡i há»£p lá»‡
		if (!VEHICLE_STATUSES.contains(newStatus)) {
			throw new BusinessException(ErrorCode.INVALID_VEHICLE_STATUS,
					"Tráº¡ng thÃ¡i '" + newStatus + "' khÃ´ng há»£p lá»‡.");
		}
		// B2: láº¥y xe + kiá»ƒm quyá»n chi nhÃ¡nh
		Vehicle v = vehicleRepository.findManagedDetailById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		// B3: chan chuyen trang thai sai state machine (admin override duoc nhung can note)
		assertVehicleTransitionAllowed(v.getStatus(), newStatus, isAdmin, note);
		if ("Available".equals(newStatus)) {
			depositService.syncOpenDepositsWhenVehicleSetAvailable(vehicleId);
		}
		v.setStatus(newStatus);
		vehicleRepository.save(v);
		evictVehicleCaches(vehicleId);
		return toManagedDetailDto(v);
	}

	/** Äá»•i tráº¡ng thÃ¡i xe hÃ ng loáº¡t â€” Fail-Fast: náº¿u báº¥t ká»³ xe nÃ o ngoÃ i chi nhÃ¡nh â†’ 403. */
	@Transactional
	public void bulkChangeStatus(java.util.List<Long> vehicleIds, String newStatus, String note,
			long actorUserId, boolean isAdmin) {
		// B1: kiá»ƒm tra input
		if (vehicleIds == null || vehicleIds.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_VEHICLE_LIST, "Danh sÃ¡ch xe rá»—ng.");
		}
		if (!VEHICLE_STATUSES.contains(newStatus)) {
			throw new BusinessException(ErrorCode.INVALID_VEHICLE_STATUS,
					"Tráº¡ng thÃ¡i '" + newStatus + "' khÃ´ng há»£p lá»‡.");
		}
		// B2: láº¥y tá»«ng xe, kiá»ƒm quyá»n + transition + cáº­p nháº­t
		for (Long id : vehicleIds) {
			Vehicle v = vehicleRepository.findManagedDetailById(id)
					.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND,
							"KhÃ´ng tÃ¬m tháº¥y xe ID=" + id + "."));
			assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
			assertVehicleTransitionAllowed(v.getStatus(), newStatus, isAdmin, note);
			if ("Available".equals(newStatus)) {
				depositService.syncOpenDepositsWhenVehicleSetAvailable(id);
			}
			v.setStatus(newStatus);
			vehicleRepository.save(v);
			evictVehicleCaches(id);
		}
	}

	// Chan chuyen trang thai xe trai state machine.
	// Logic:
	// - Admin override: duoc phep moi transition, nhung BAT BUOC co note (audit).
	// - Staff/Manager: chi duoc chuyen theo ALLOWED_VEHICLE_TRANSITIONS.
	// - Neu from == to thi bo qua (khong coi la transition).
	private void assertVehicleTransitionAllowed(String from, String to, boolean isAdmin, String note) {
		if (from == null || from.equals(to)) {
			return;
		}
		if (isAdmin) {
			if (note == null || note.isBlank()) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Admin override tráº¡ng thÃ¡i xe báº¯t buá»™c pháº£i cÃ³ lÃ½ do (note).");
			}
			return;
		}
		Set<String> allowed = ALLOWED_VEHICLE_TRANSITIONS.getOrDefault(from, Set.of());
		if (!allowed.contains(to)) {
			throw new BusinessException(ErrorCode.INVALID_VEHICLE_STATE_TRANSITION,
					"Chuyá»ƒn tráº¡ng thÃ¡i xe khÃ´ng há»£p lá»‡: " + from + " -> " + to
							+ ". Chá»‰ Admin má»›i cÃ³ thá»ƒ override (kÃ¨m lÃ½ do).");
		}
	}

	/** XÃ³a má»m xe hÃ ng loáº¡t â€” Fail-Fast: náº¿u báº¥t ká»³ xe nÃ o ngoÃ i chi nhÃ¡nh â†’ 403. */
	@Transactional
	public void bulkSoftDelete(java.util.List<Long> vehicleIds, long actorUserId, boolean isAdmin) {
		if (vehicleIds == null || vehicleIds.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_VEHICLE_LIST, "Danh sÃ¡ch xe rá»—ng.");
		}
		for (Long id : vehicleIds) {
			Vehicle v = vehicleRepository.findManagedDetailById(id)
					.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND,
							"KhÃ´ng tÃ¬m tháº¥y xe ID=" + id + "."));
			assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
			if (!v.isDeleted()) {
				v.setDeleted(true);
				vehicleRepository.save(v);
				evictVehicleCaches(id);
			}
		}
	}

	/** ThÃªm áº£nh vÃ o xe â€” áº£nh Ä‘Ã£ upload Cloudinary, chá»‰ lÆ°u URL vÃ o DB. */
	@Transactional
	public List<VehicleImageDto> addVehicleImages(long vehicleId, List<VehicleImageWriteDto> images,
			long actorUserId, boolean isAdmin) {
		// B1: láº¥y xe + kiá»ƒm quyá»n
		Vehicle v = vehicleRepository.findManagedDetailById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		// B2: táº¡o entity áº£nh tá»« DTO
		for (VehicleImageWriteDto d : images) {
			VehicleImage img = new VehicleImage();
			img.setVehicle(v);
			img.setImageUrl(d.getUrl().trim());
			img.setSortOrder(d.getSortOrder() != null ? d.getSortOrder() : 0);
			img.setPrimaryImage(Boolean.TRUE.equals(d.getPrimaryImage()));
			v.getImages().add(img);
		}
		vehicleRepository.save(v);
		evictVehicleCaches(vehicleId);
		// B3: tráº£ láº¡i danh sÃ¡ch áº£nh hiá»‡n táº¡i
		return mapVehicleImagesToDtos(v);
	}

	/** XÃ³a 1 áº£nh xe â€” chá»‰ xÃ³a record DB (áº£nh trÃªn Cloudinary giá»¯ nguyÃªn). */
	@Transactional
	public void deleteVehicleImage(long vehicleId, long imageId, long actorUserId, boolean isAdmin) {
		// B1: láº¥y xe + kiá»ƒm quyá»n
		Vehicle v = vehicleRepository.findManagedDetailById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		// B2: tÃ¬m áº£nh thuá»™c xe
		VehicleImage img = vehicleImageRepository.findByIdAndVehicle_Id(imageId, vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y áº£nh."));
		// B3: xÃ³a khá»i DB
		v.getImages().remove(img);
		vehicleRepository.save(v);
		evictVehicleCaches(vehicleId);
	}

	public void evictPublicVehicleCaches(long vehicleId) {
		evictVehicleCaches(vehicleId);
	}

	public void evictPublicCachesForBranch(int branchId) {
		Cache list = cacheManager.getCache("vehicleList");
		if (list != null) {
			list.clear();
		}
		Cache facets = cacheManager.getCache("vehicleFacets");
		if (facets != null) {
			facets.clear();
		}
		Cache detail = cacheManager.getCache("vehicleDetail");
		if (detail != null) {
			for (Long id : vehicleRepository.findIdsByBranchIdAndDeletedFalse(branchId)) {
				detail.evict(detailCacheKey(id));
			}
		}
	}

	private void evictVehicleCaches(Long vehicleId) {
		Cache list = cacheManager.getCache("vehicleList");
		if (list != null) {
			list.clear();
		}
		Cache facets = cacheManager.getCache("vehicleFacets");
		if (facets != null) {
			facets.clear();
		}
		Cache detail = cacheManager.getCache("vehicleDetail");
		if (detail != null) {
			detail.evict(detailCacheKey(vehicleId));
		}
	}

	private static String detailCacheKey(long id) {
		return VEHICLE_DETAIL_CACHE_PREFIX + id;
	}

	private static Long parsePositiveLong(String raw) {
		try {
			long parsed = Long.parseLong(raw);
			return parsed > 0 ? parsed : null;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String normalizeListSortKey(String sort) {
		if (sort == null || sort.isBlank()) {
			return "postingDateDesc";
		}
		String s = sort.trim();
		if ("postingDateDesc".equalsIgnoreCase(s) || "posting_date_desc".equalsIgnoreCase(s)) {
			return "postingDateDesc";
		}
		if ("priceAsc".equalsIgnoreCase(s) || "price_asc".equalsIgnoreCase(s)) {
			return "priceAsc";
		}
		if ("priceDesc".equalsIgnoreCase(s) || "price_desc".equalsIgnoreCase(s)) {
			return "priceDesc";
		}
		if ("yearDesc".equalsIgnoreCase(s) || "year_desc".equalsIgnoreCase(s)) {
			return "yearDesc";
		}
		if ("idDesc".equalsIgnoreCase(s) || "id_desc".equalsIgnoreCase(s)) {
			return "idDesc";
		}
		return "postingDateDesc";
	}

	private static Sort listSortForPublicList(String sortKey) {
		if ("postingDateDesc".equals(sortKey)) {
			return Sort.by(Order.desc("postingDate").with(Sort.NullHandling.NULLS_LAST), Order.desc("id"));
		}
		if ("priceAsc".equals(sortKey)) {
			return Sort.by(Order.asc("price").with(Sort.NullHandling.NULLS_LAST), Order.desc("id"));
		}
		if ("priceDesc".equals(sortKey)) {
			return Sort.by(Order.desc("price").with(Sort.NullHandling.NULLS_LAST), Order.desc("id"));
		}
		if ("yearDesc".equals(sortKey)) {
			return Sort.by(Order.desc("year").with(Sort.NullHandling.NULLS_LAST), Order.desc("id"));
		}
		return Sort.by(Order.desc("id"));
	}

	private static String buildListCacheKey(Integer categoryId, Integer subcategoryId, BigDecimal minPrice,
			BigDecimal maxPrice, Integer yearMin, Integer yearMax, String transmission, Integer branchId, int page,
			int size, String sortKey) {
		String c = categoryId != null ? String.valueOf(categoryId) : "all";
		String sub = subcategoryId != null ? String.valueOf(subcategoryId) : "all";
		String min = minPrice != null ? minPrice.toString() : "x";
		String max = maxPrice != null ? maxPrice.toString() : "x";
		String y1 = yearMin != null ? String.valueOf(yearMin) : "x";
		String y2 = yearMax != null ? String.valueOf(yearMax) : "x";
		String tr = transmission != null ? transmission : "x";
		String br = branchId != null ? String.valueOf(branchId) : "all";
		return VEHICLE_LIST_CACHE_PREFIX + c + "|" + sub + "|" + min + "|" + max + "|" + y1 + "|" + y2 + "|" + tr + "|"
				+ br + "|" + page + "|" + size + "|" + sortKey;
	}

	private static String buildManagerListCacheKey(long actorUserId, boolean isAdmin, Integer categoryId,
			Integer subcategoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer yearMin, Integer yearMax,
			String transmission, Integer branchId, int page, int size, String sortKey, String scope, String status,
			String excludeStatus) {
		String role = isAdmin ? "admin" : "managed";
		String c = categoryId != null ? String.valueOf(categoryId) : "all";
		String sub = subcategoryId != null ? String.valueOf(subcategoryId) : "all";
		String min = minPrice != null ? minPrice.toString() : "x";
		String max = maxPrice != null ? maxPrice.toString() : "x";
		String y1 = yearMin != null ? String.valueOf(yearMin) : "x";
		String y2 = yearMax != null ? String.valueOf(yearMax) : "x";
		String tr = transmission != null ? transmission : "x";
		String br = branchId != null ? String.valueOf(branchId) : "all";
		String sc = scope != null ? scope : "mine";
		String st = status != null ? status : "all";
		String ex = excludeStatus != null ? excludeStatus : "x";
		return VEHICLE_LIST_CACHE_PREFIX + "mgr|" + role + "|" + actorUserId + "|" + sc + "|" + c + "|" + sub + "|"
				+ min + "|" + max + "|" + y1 + "|" + y2 + "|" + tr + "|" + br + "|" + page + "|" + size + "|"
				+ sortKey + "|" + st + "|" + ex;
	}

	/** CÃ¡c chi nhÃ¡nh user Ä‘Æ°á»£c phÃ©p thao tÃ¡c (manager_id + phÃ¢n cÃ´ng active). */
	private List<Integer> resolveManageableBranchIds(long actorUserId) {
		LinkedHashSet<Integer> set = new LinkedHashSet<>();
		for (Branch b : branchRepository.findAllByManager_IdAndDeletedFalse(actorUserId)) {
			set.add(b.getId());
		}
		for (StaffAssignment sa : staffAssignmentRepository.findByUserIdAndActiveTrue(actorUserId)) {
			if (sa.getBranchId() != null) {
				set.add(sa.getBranchId());
			}
		}
		return new ArrayList<>(set);
	}

	private static VehicleListResponse emptyListResponse(int page, int size) {
		PageMetaDto meta = new PageMetaDto();
		meta.setPage(page);
		meta.setSize(size);
		meta.setTotalElements(0);
		meta.setTotalPages(0);
		VehicleListResponse res = new VehicleListResponse();
		res.setItems(new ArrayList<>());
		res.setMeta(meta);
		return res;
	}

	private VehicleListResponse buildManagerListResponse(Page<Vehicle> p) {
		List<VehicleSummaryDto> items = new ArrayList<>();
		for (Vehicle v : p.getContent()) {
			items.add(toSummaryDto(v));
		}
		enrichSummariesListingHold(items, p.getContent());
		PageMetaDto meta = new PageMetaDto();
		meta.setPage(p.getNumber());
		meta.setSize(p.getSize());
		meta.setTotalElements(p.getTotalElements());
		meta.setTotalPages(p.getTotalPages());
		VehicleListResponse res = new VehicleListResponse();
		res.setItems(items);
		res.setMeta(meta);
		return res;
	}

	private VehicleListResponse loadListFromDatabase(String keyword, Integer categoryId, Integer subcategoryId,
			BigDecimal minPrice, BigDecimal maxPrice, Integer yearMin, Integer yearMax, String transmission,
			Integer branchId, int page, int size, String sortKey) {
		Sort sort = listSortForPublicList(sortKey);
		PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), sort);
		Page<Vehicle> p = vehicleRepository.findPublicPage(keyword, categoryId, subcategoryId, minPrice, maxPrice,
				yearMin, yearMax, transmission, branchId, pr);
		List<VehicleSummaryDto> items = new ArrayList<>();
		for (Vehicle v : p.getContent()) {
			items.add(toSummaryDto(v));
		}
		enrichSummariesListingHold(items, p.getContent());
		PageMetaDto meta = new PageMetaDto();
		meta.setPage(p.getNumber());
		meta.setSize(p.getSize());
		meta.setTotalElements(p.getTotalElements());
		meta.setTotalPages(p.getTotalPages());
		VehicleListResponse res = new VehicleListResponse();
		res.setItems(items);
		res.setMeta(meta);
		return res;
	}

	private VehicleDetailDto toPublicDetailDto(Vehicle v) {
		VehicleDetailDto dto = toDetailDto(v);
		dto.setListingHoldActive(computeListingHoldActive(v.getId()));
		return dto;
	}

	private VehicleDetailDto toManagedDetailDto(Vehicle v) {
		VehicleDetailDto dto = toDetailDto(v);
		dto.setListingHoldActive(computeListingHoldActive(v.getId()));
		return dto;
	}

	private boolean computeListingHoldActive(long vehicleId) {
		return depositRepository.countByVehicleIdAndStatusIn(vehicleId,
				List.of("Pending", "Confirmed", "AwaitingPayment")) > 0;
	}

	private void enrichSummariesListingHold(List<VehicleSummaryDto> items, List<Vehicle> vehicles) {
		if (items.isEmpty() || vehicles.isEmpty() || items.size() != vehicles.size()) {
			return;
		}
		List<Long> ids = vehicles.stream().map(Vehicle::getId).toList();
		Map<Long, Long> counts = loadActiveSalesHoldCounts(ids);
		for (int i = 0; i < items.size(); i++) {
			long vid = vehicles.get(i).getId();
			items.get(i).setListingHoldActive(counts.getOrDefault(vid, 0L) > 0);
		}
	}

	private Map<Long, Long> loadActiveSalesHoldCounts(List<Long> vehicleIds) {
		if (vehicleIds.isEmpty()) {
			return Map.of();
		}
		List<Object[]> rows = depositRepository.countActiveSalesHoldsGrouped(vehicleIds);
		Map<Long, Long> out = new HashMap<>();
		for (Object[] row : rows) {
			out.put((Long) row[0], (Long) row[1]);
		}
		return out;
	}

	private Subcategory loadSubcategoryForCategory(int subcategoryId, int categoryId) {
		return subcategoryRepository.findByIdAndCategory_Id(subcategoryId, categoryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MODEL_NOT_FOUND,
						"KhÃ´ng tÃ¬m tháº¥y dÃ²ng xe hoáº·c khÃ´ng thuá»™c hÃ£ng."));
	}

	private Branch loadBranchAndAssertManager(int branchId, long actorUserId, boolean isAdmin) {
		Branch branch = branchRepository.findActiveByIdWithManager(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y chi nhÃ¡nh."));
		assertCanManageBranch(actorUserId, isAdmin, branch);
		return branch;
	}

	private static void copyCreateRequestToVehicle(VehicleCreateRequest req, Vehicle v, long actorUserId) {
		v.setTitle(req.getTitle().trim());
		v.setPrice(req.getPrice());
		v.setDescription(req.getDescription());
		v.setYear(req.getYear());
		v.setFuel(req.getFuel());
		v.setTransmission(req.getTransmission());
		v.setMileage(req.getMileage());
		v.setBodyStyle(req.getBodyStyle());
		v.setOrigin(req.getOrigin());
		v.setPostingDate(req.getPostingDate());
		v.setStatus("Available");
		v.setDeleted(false);
		v.setCreatedBy(actorUserId);
	}

	private static void copyUpdateRequestToVehicle(VehicleUpdateRequest req, Vehicle v, Category category, Subcategory sub,
			Branch branch) {
		v.setCategory(category);
		v.setSubcategory(sub);
		v.setBranch(branch);
		v.setTitle(req.getTitle().trim());
		v.setPrice(req.getPrice());
		v.setDescription(req.getDescription());
		v.setYear(req.getYear());
		v.setFuel(req.getFuel());
		v.setTransmission(req.getTransmission());
		v.setMileage(req.getMileage());
		v.setBodyStyle(req.getBodyStyle());
		v.setOrigin(req.getOrigin());
		v.setPostingDate(req.getPostingDate());
		v.setStatus(req.getStatus());
	}

	/**
	 * CÃ³ StaffAssignment active Ä‘Ãºng chi nhÃ¡nh â€” duyá»‡t list thay vÃ¬ exists* derived query (trÃ¡nh edge case Spring Data).
	 */
	private boolean hasActiveAssignmentAtBranch(long actorUserId, Integer branchId) {
		if (branchId == null) {
			return false;
		}
		for (StaffAssignment sa : staffAssignmentRepository.findByUserIdAndActiveTrue(actorUserId)) {
			if (branchId.equals(sa.getBranchId())) {
				return true;
			}
		}
		return false;
	}

	private void assertCanManageBranch(long actorUserId, boolean isAdmin, Branch branch) {
		if (isAdmin) {
			return;
		}
		Integer bid = branch.getId();
		User manager = branch.getManager();
		if (manager != null && Objects.equals(manager.getId(), actorUserId)) {
			return;
		}
		// Truy váº¥n trá»±c tiáº¿p (phÃ²ng branch.manager chÆ°a khá»›p entity trong session)
		if (branchRepository.findFirstByManager_IdAndDeletedFalse(actorUserId).filter(b -> b.getId().equals(bid))
				.isPresent()) {
			return;
		}
		if (hasActiveAssignmentAtBranch(actorUserId, bid)) {
			return;
		}
		String bname = branch.getName() != null ? branch.getName() : "?";
		throw new BusinessException(ErrorCode.FORBIDDEN, String.format(
				"KhÃ´ng cÃ³ quyá»n thao tÃ¡c xe thuá»™c chi nhÃ¡nh \"%s\" (id=%d). Cáº§n: Admin; hoáº·c tÃ i khoáº£n lÃ  manager_id cá»§a chi nhÃ¡nh; hoáº·c cÃ³ StaffAssignments Ä‘ang active táº¡i chi nhÃ¡nh Ä‘Ã³.",
				bname, bid));
	}

	/** Public wrapper â€” dÃ¹ng bá»Ÿi MaintenanceService Ä‘á»ƒ kiá»ƒm quyá»n chi nhÃ¡nh. */
	public void assertCanManageBranchPublic(long actorUserId, boolean isAdmin, Branch branch) {
		assertCanManageBranch(actorUserId, isAdmin, branch);
	}

	private static void applyImagesFromRequest(Vehicle v, List<VehicleImageWriteDto> dtos) {
		if (dtos == null) {
			return;
		}
		for (VehicleImageWriteDto d : dtos) {
			VehicleImage img = new VehicleImage();
			img.setVehicle(v);
			img.setImageUrl(d.getUrl().trim());
			img.setSortOrder(d.getSortOrder() != null ? d.getSortOrder() : 0);
			img.setPrimaryImage(Boolean.TRUE.equals(d.getPrimaryImage()));
			v.getImages().add(img);
		}
	}

	// Sinh chuá»—i sá»‘ ngáº«u nhiÃªn (LISTING_ID_DIGITS kÃ½ tá»±), kiá»ƒm tra trÃ¹ng listing_id trong DB â€” khÃ´ng dÃ¹ng lÃ m PK.
	private String nextRandomUniqueListingId() {
		for (int attempt = 0; attempt < LISTING_ID_MAX_ATTEMPTS; attempt++) {
			String candidate = randomNumericListingId(LISTING_ID_DIGITS);
			if (!vehicleRepository.existsByListingId(candidate)) {
				return candidate;
			}
		}
		throw new BusinessException(ErrorCode.LISTING_ID_CONFLICT, "KhÃ´ng táº¡o Ä‘Æ°á»£c mÃ£ tin duy nháº¥t, vui lÃ²ng thá»­ láº¡i.");
	}

	private String randomNumericListingId(int digits) {
		StringBuilder sb = new StringBuilder(digits);
		sb.append(1 + listingIdRandom.nextInt(9));
		for (int i = 1; i < digits; i++) {
			sb.append(listingIdRandom.nextInt(10));
		}
		return sb.toString();
	}

	private static String pickPrimaryImageUrl(Vehicle v) {
		for (VehicleImage i : v.getImages()) {
			if (i.isPrimaryImage()) {
				return i.getImageUrl();
			}
		}
		if (v.getImages().isEmpty()) {
			return null;
		}
		return v.getImages().get(0).getImageUrl();
	}

	private static VehicleSummaryDto toSummaryDto(Vehicle v) {
		VehicleSummaryDto dto = new VehicleSummaryDto();
		dto.setId(v.getId());
		dto.setListingId(v.getListingId());
		dto.setTitle(v.getTitle());
		dto.setPrice(v.getPrice());
		dto.setYear(v.getYear());
		dto.setMileage(v.getMileage());
		dto.setFuel(v.getFuel());
		dto.setTransmission(v.getTransmission());
		dto.setCategoryId(v.getCategory().getId());
		dto.setCategoryName(v.getCategory().getName());
		dto.setSubcategoryId(v.getSubcategory().getId());
		dto.setSubcategoryName(v.getSubcategory().getName());
		dto.setBranchId(v.getBranch().getId());
		dto.setBranchName(v.getBranch().getName());
		dto.setStatus(v.getStatus());
		dto.setDeleted(v.isDeleted());
		dto.setPrimaryImageUrl(pickPrimaryImageUrl(v));
		dto.setUpdatedAt(v.getUpdatedAt());
		return dto;
	}

	private static VehicleDetailDto toDetailDto(Vehicle v) {
		VehicleDetailDto dto = new VehicleDetailDto();
		fillDetailDtoScalars(v, dto);
		fillDetailDtoRefs(v, dto);
		dto.setImages(mapVehicleImagesToDtos(v));
		return dto;
	}

	private static void fillDetailDtoScalars(Vehicle v, VehicleDetailDto dto) {
		dto.setId(v.getId());
		dto.setListingId(v.getListingId());
		dto.setTitle(v.getTitle());
		dto.setPrice(v.getPrice());
		dto.setDescription(v.getDescription());
		dto.setYear(v.getYear());
		dto.setFuel(v.getFuel());
		dto.setTransmission(v.getTransmission());
		dto.setMileage(v.getMileage());
		dto.setBodyStyle(v.getBodyStyle());
		dto.setOrigin(v.getOrigin());
		dto.setPostingDate(v.getPostingDate());
		dto.setStatus(v.getStatus());
		dto.setDeleted(v.isDeleted());
	}

	private static void fillDetailDtoRefs(Vehicle v, VehicleDetailDto dto) {
		dto.setCategoryId(v.getCategory().getId());
		dto.setCategoryName(v.getCategory().getName());
		dto.setSubcategoryId(v.getSubcategory().getId());
		dto.setSubcategoryName(v.getSubcategory().getName());
		dto.setBranchId(v.getBranch().getId());
		dto.setBranchName(v.getBranch().getName());
	}

	private static List<VehicleImageDto> mapVehicleImagesToDtos(Vehicle v) {
		List<VehicleImageDto> imgs = new ArrayList<>();
		for (VehicleImage i : v.getImages()) {
			VehicleImageDto d = new VehicleImageDto();
			d.setId(i.getId());
			d.setUrl(i.getImageUrl());
			d.setSortOrder(i.getSortOrder());
			d.setPrimaryImage(i.isPrimaryImage());
			imgs.add(d);
		}
		return imgs;
	}

	// ===================== XUáº¤T EXCEL DANH SÃCH XE =====================

	// Xuáº¥t Excel danh sÃ¡ch xe cho manager/admin â€” há»— trá»£ lá»c theo status vÃ  keyword (tÃ¬m theo title, listingId, hÃ£ng/dÃ²ng xe)
	@Transactional(readOnly = true)
	public byte[] exportVehiclesExcel(long actorUserId, boolean isAdmin, String status, String keyword) {
		// B1: láº¥y danh sÃ¡ch xe (khÃ´ng phÃ¢n trang, tá»‘i Ä‘a 5000)
		List<Integer> branchIds;
		if (isAdmin) {
			branchIds = branchRepository.findAllByDeletedFalseOrderByIdAsc().stream().map(Branch::getId).toList();
		} else {
			branchIds = resolveManageableBranchIds(actorUserId);
		}
		if (branchIds.isEmpty()) {
			return buildVehicleExcelBytes(List.of());
		}

		// B2: query vá»›i filter status (náº¿u cÃ³)
		String vehicleStatus = (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) ? status.trim() : null;
		Page<Vehicle> page = vehicleRepository.findManagedPage(branchIds, null, null, null, null,
				null, null, null, null, null, vehicleStatus, null,
				PageRequest.of(0, 5000, Sort.by(Sort.Order.desc("id"))));

		// B3: lá»c theo keyword trong bá»™ nhá»› (title, listingId, tÃªn hÃ£ng/dÃ²ng xe)
		List<Vehicle> result = page.getContent();
		if (keyword != null && !keyword.isBlank()) {
			String kw = keyword.trim().toLowerCase();
			result = result.stream().filter(v -> {
				if (v.getTitle() != null && v.getTitle().toLowerCase().contains(kw)) return true;
				if (v.getListingId() != null && v.getListingId().toLowerCase().contains(kw)) return true;
				if (v.getCategory() != null && v.getCategory().getName() != null && v.getCategory().getName().toLowerCase().contains(kw)) return true;
				if (v.getSubcategory() != null && v.getSubcategory().getName() != null && v.getSubcategory().getName().toLowerCase().contains(kw)) return true;
				return false;
			}).toList();
		}
		return buildVehicleExcelBytes(result);
	}

	private byte[] buildVehicleExcelBytes(List<Vehicle> vehicles) {
		try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
			org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Danh sÃ¡ch xe");
			String[] headers = {"ID", "MÃ£ tin", "TiÃªu Ä‘á»", "HÃ£ng xe", "DÃ²ng xe", "NÄƒm SX", "GiÃ¡ (VNÄ)", "Sá»‘ km", "NhiÃªn liá»‡u", "Há»™p sá»‘", "Tráº¡ng thÃ¡i", "Chi nhÃ¡nh"};
			org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				headerRow.createCell(i).setCellValue(headers[i]);
			}
			int rowIdx = 1;
			for (Vehicle v : vehicles) {
				org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
				row.createCell(0).setCellValue(v.getId());
				row.createCell(1).setCellValue(v.getListingId() != null ? v.getListingId() : "");
				row.createCell(2).setCellValue(v.getTitle() != null ? v.getTitle() : "");
				row.createCell(3).setCellValue(v.getCategory() != null ? v.getCategory().getName() : "");
				row.createCell(4).setCellValue(v.getSubcategory() != null ? v.getSubcategory().getName() : "");
				row.createCell(5).setCellValue(v.getYear() != null ? v.getYear() : 0);
				row.createCell(6).setCellValue(v.getPrice() != null ? v.getPrice().doubleValue() : 0);
				row.createCell(7).setCellValue(v.getMileage() != null ? v.getMileage() : 0);
				row.createCell(8).setCellValue(v.getFuel() != null ? v.getFuel() : "");
				row.createCell(9).setCellValue(v.getTransmission() != null ? v.getTransmission() : "");
				row.createCell(10).setCellValue(v.getStatus() != null ? v.getStatus() : "");
				row.createCell(11).setCellValue(v.getBranch() != null ? v.getBranch().getName() : "");
			}
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			wb.write(out);
			return out.toByteArray();
		} catch (java.io.IOException e) {
			throw new RuntimeException("Lá»—i táº¡o file Excel", e);
		}
	}

	// ===================== Gá»¢I Ã TÃŒM KIáº¾M (Search Autocomplete) =====================

	// Tráº£ vá» danh sÃ¡ch gá»£i Ã½ tÃ¬m kiáº¿m tá»« 3 nguá»“n: hÃ£ng/dÃ²ng xe, title xe, nÄƒm sáº£n xuáº¥t
	@Transactional(readOnly = true)
	public List<SuggestionDto> getSuggestions(String q, int limit) {
		// B1: Validate Ä‘áº§u vÃ o
		if (q == null || q.trim().length() < 2) {
			return List.of();
		}
		String keyword = q.trim();
		String keywordLower = keyword.toLowerCase();
		List<SuggestionDto> results = new ArrayList<>();

		// B2: TÃ¬m trong Subcategories (hÃ£ng/dÃ²ng xe) â€” Æ°u tiÃªn cao nháº¥t
		List<Object[]> brandPairs = subcategoryRepository.findSuggestionsByKeyword(
				keyword, PageRequest.of(0, limit));
		// GhÃ©p text: náº¿u subcategory.name Ä‘Ã£ chá»©a category.name á»Ÿ Ä‘áº§u thÃ¬ chá»‰ dÃ¹ng subcategory.name
		List<String> brandNames = new ArrayList<>();
		for (Object[] pair : brandPairs) {
			String catName = (String) pair[0];
			String subName = (String) pair[1];
			if (subName.toLowerCase().startsWith(catName.toLowerCase())) {
				brandNames.add(subName);
			} else {
				brandNames.add(catName + " " + subName);
			}
		}
		// Sáº¯p xáº¿p: prefix match lÃªn trÆ°á»›c, contains match xuá»‘ng sau
		brandNames.sort((a, b) -> {
			boolean aPrefix = a.toLowerCase().startsWith(keywordLower);
			boolean bPrefix = b.toLowerCase().startsWith(keywordLower);
			if (aPrefix != bPrefix) return aPrefix ? -1 : 1;
			return a.compareToIgnoreCase(b);
		});
		for (String name : brandNames) {
			SuggestionDto dto = new SuggestionDto();
			dto.setType("brand");
			dto.setText(name);
			results.add(dto);
		}

		// B3: TÃ¬m trong Vehicles.title (xe cá»¥ thá»ƒ Ä‘ang bÃ¡n)
		List<String> titles = vehicleRepository.findTitleSuggestions(
				keyword, PageRequest.of(0, limit));
		// Sáº¯p xáº¿p: prefix match lÃªn trÆ°á»›c
		titles.sort((a, b) -> {
			boolean aPrefix = a.toLowerCase().startsWith(keywordLower);
			boolean bPrefix = b.toLowerCase().startsWith(keywordLower);
			if (aPrefix != bPrefix) return aPrefix ? -1 : 1;
			return a.compareToIgnoreCase(b);
		});
		for (String title : titles) {
			SuggestionDto dto = new SuggestionDto();
			dto.setType("vehicle");
			dto.setText(title);
			results.add(dto);
		}

		// B4: Náº¿u keyword lÃ  chuá»—i sá»‘ â†’ tÃ¬m nÄƒm sáº£n xuáº¥t
		if (keyword.matches("\\d+")) {
			List<Integer> years = vehicleRepository.findDistinctYears();
			for (Integer year : years) {
				if (String.valueOf(year).startsWith(keyword)) {
					SuggestionDto dto = new SuggestionDto();
					dto.setType("year");
					dto.setText(String.valueOf(year));
					results.add(dto);
				}
			}
		}

		// B5: Loáº¡i bá» káº¿t quáº£ trÃ¹ng text (giá»¯ káº¿t quáº£ Ä‘áº§u tiÃªn â€” Æ°u tiÃªn brand > vehicle > year)
		Set<String> seen = new LinkedHashSet<>();
		List<SuggestionDto> unique = new ArrayList<>();
		for (SuggestionDto dto : results) {
			String key = dto.getText().toLowerCase();
			if (seen.add(key)) {
				unique.add(dto);
			}
		}

		// B6: Giá»›i háº¡n tá»‘i Ä‘a limit káº¿t quáº£
		if (unique.size() > limit) {
			return unique.subList(0, limit);
		}
		return unique;
	}

}
