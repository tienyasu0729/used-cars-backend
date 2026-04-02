package scu.dn.used_cars_backend.service;

// Service xử lý logic xe: danh sách/chi tiết công khai, lưu xe cho khách, tạo-sửa-xóa cho manager.
// Map DTO thủ công trong service (không dùng MapStruct). Cache đọc/ghi bằng CacheManager cho dễ hiểu.

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleCreateRequest;
import scu.dn.used_cars_backend.dto.vehicle.VehicleDetailDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleImageDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleImageWriteDto;
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
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.SubcategoryRepository;
import scu.dn.used_cars_backend.repository.VehicleImageRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class VehicleService {

	private static final Set<String> VEHICLE_STATUSES = Set.of("Available", "Reserved", "Sold", "Hidden", "InTransfer");
	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
	/** Tiền tố khóa cache — đổi khi DTO list/detail thay đổi để tránh trả bản cũ thiếu field. */
	private static final String VEHICLE_LIST_CACHE_PREFIX = "v3:";
	private static final String VEHICLE_DETAIL_CACHE_PREFIX = "v3:";
	/** Mã tin (listing_id): chuỗi số ngẫu nhiên, không phải khóa chính; cột DB unique, độ dài tối đa 20. */
	private static final int LISTING_ID_DIGITS = 12;
	private static final int LISTING_ID_MAX_ATTEMPTS = 20;

	private final VehicleRepository vehicleRepository;
	private final VehicleImageRepository vehicleImageRepository;
	private final CategoryRepository categoryRepository;
	private final SubcategoryRepository subcategoryRepository;
	private final BranchRepository branchRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final CacheManager cacheManager;
	private final SecureRandom listingIdRandom = new SecureRandom();

	public VehicleService(VehicleRepository vehicleRepository, VehicleImageRepository vehicleImageRepository,
			CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository,
			BranchRepository branchRepository, StaffAssignmentRepository staffAssignmentRepository,
			CacheManager cacheManager) {
		this.vehicleRepository = vehicleRepository;
		this.vehicleImageRepository = vehicleImageRepository;
		this.categoryRepository = categoryRepository;
		this.subcategoryRepository = subcategoryRepository;
		this.branchRepository = branchRepository;
		this.staffAssignmentRepository = staffAssignmentRepository;
		this.cacheManager = cacheManager;
	}

	@Transactional(readOnly = true)
	public VehicleListResponse listPublic(Integer categoryId, Integer subcategoryId, BigDecimal minPrice,
			BigDecimal maxPrice, Integer yearMin, Integer yearMax, String transmission, Integer branchId, int page,
			int size, String sort, String keyword) {
		String tx = transmission != null && !transmission.isBlank() ? transmission.trim() : null;
		String kw = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
		String sortKey = normalizeListSortKey(sort);

		// Khi có keyword thì bỏ cache (keyword rất đa dạng, cache key sẽ phình to)
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

	@Transactional(readOnly = true)
	public VehicleDetailDto getPublicDetail(long id) {
		// B1: thử lấy từ cache chi tiết
		String key = detailCacheKey(id);
		Cache cache = cacheManager.getCache("vehicleDetail");
		if (cache != null) {
			Cache.ValueWrapper w = cache.get(key);
			if (w != null && w.get() != null) {
				return (VehicleDetailDto) w.get();
			}
		}
		// B2: DB; không tìm thấy thì không put cache (giống unless = #result == null)
		VehicleDetailDto dto = vehicleRepository.findPublicDetailById(id).map(VehicleService::toDetailDto).orElse(null);
		if (dto != null && cache != null) {
			cache.put(key, dto);
		}
		return dto;
	}

	/**
	 * Danh sách xe trong phạm vi quản lý: Admin xem mọi chi nhánh; manager/staff chỉ chi nhánh
	 * (manager_id hoặc StaffAssignments active). Khác GET /vehicles công khai: gồm cả xe đã ẩn (deleted/Hidden).
	 * scope=NETWORK: toàn hệ thống nhưng chỉ xe còn hiển thị công khai (deleted=false, status khác Hidden) — tra cứu điều chuyển.
	 */
	@Transactional(readOnly = true)
	public VehicleListResponse listForManager(long actorUserId, boolean isAdmin, Integer categoryId,
			Integer subcategoryId, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, Integer yearMin,
			Integer yearMax, String transmission, Integer branchId, int page, int size, String sort, String scope) {
		String tx = transmission != null && !transmission.isBlank() ? transmission.trim() : null;
		String sortKey = normalizeListSortKey(sort);
		Sort sortObj = listSortForPublicList(sortKey);
		int pg = Math.max(0, page);
		boolean networkScope = "NETWORK".equalsIgnoreCase(scope);

		// Tab tra cứu mạng lưới: cùng điều kiện list công khai, mọi chi nhánh (có thể lọc branchId)
		if (networkScope) {
			int netSz = Math.min(500, Math.max(1, size));
			PageRequest pr = PageRequest.of(pg, netSz, sortObj);
			Page<Vehicle> p = vehicleRepository.findPublicPage(null, categoryId, subcategoryId, minPrice, maxPrice,
					yearMin, yearMax, tx, branchId, pr);
			return buildVehicleListResponse(p);
		}

		int sz = Math.min(100, Math.max(1, size));
		PageRequest pr = PageRequest.of(pg, sz, sortObj);

		List<Integer> branchIds;
		if (isAdmin) {
			branchIds = branchRepository.findAllByDeletedFalseOrderByIdAsc().stream().map(Branch::getId).toList();
		} else {
			branchIds = resolveManageableBranchIds(actorUserId);
		}
		if (branchIds.isEmpty()) {
			return emptyListResponse(pg, sz);
		}
		if (branchId != null && !branchIds.contains(branchId)) {
			return emptyListResponse(pg, sz);
		}

		Page<Vehicle> p = vehicleRepository.findManagedPage(branchIds, categoryId, subcategoryId, minPrice, maxPrice,
				yearMin, yearMax, tx, branchId, pr);
		return buildVehicleListResponse(p);
	}

	/** Chi tiết xe cho màn sửa manager — 403 nếu xe không thuộc chi nhánh được quản lý. */
	@Transactional(readOnly = true)
	public VehicleDetailDto getManagedDetail(long id, long actorUserId, boolean isAdmin) {
		Vehicle v = vehicleRepository.findManagedDetailById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		return toDetailDto(v);
	}

	@Transactional
	public VehicleDetailDto createVehicle(VehicleCreateRequest req, long actorUserId, boolean isAdmin) {
		// B1: khóa category + load subcategory + branch và kiểm tra quyền
		Category category = categoryRepository.findByIdForUpdate(req.getCategoryId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND, "Không tìm thấy hãng."));
		Subcategory sub = loadSubcategoryForCategory(req.getSubcategoryId(), req.getCategoryId());
		Branch branch = loadBranchAndAssertManager(req.getBranchId(), actorUserId, isAdmin);

		// B2: tạo entity + listing_id (số ngẫu nhiên duy nhất, khác id khóa chính) + ảnh
		Vehicle v = new Vehicle();
		v.setListingId(nextRandomUniqueListingId());
		v.setCategory(category);
		v.setSubcategory(sub);
		v.setBranch(branch);
		copyCreateRequestToVehicle(req, v, actorUserId);
		applyImagesFromRequest(v, req.getImages());

		Vehicle saved = vehicleRepository.save(v);
		evictVehicleCaches(saved.getId());
		return toDetailDto(saved);
	}

	@Transactional
	public VehicleDetailDto updateVehicle(long id, VehicleUpdateRequest req, long actorUserId, boolean isAdmin) {
		// B1: lấy xe + quyền trên chi nhánh (kể cả xe đã ẩn khỏi công khai)
		Vehicle v = vehicleRepository.findManagedDetailById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());

		// B2: load ref mới + validate status
		Category category = categoryRepository.findById(req.getCategoryId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND, "Không tìm thấy hãng."));
		Subcategory sub = loadSubcategoryForCategory(req.getSubcategoryId(), req.getCategoryId());
		Branch branch = loadBranchAndAssertManager(req.getBranchId(), actorUserId, isAdmin);
		if (!VEHICLE_STATUSES.contains(req.getStatus())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Trạng thái xe không hợp lệ.");
		}

		// B3: ghi đè field + thay ảnh
		boolean wasDeleted = v.isDeleted();
		copyUpdateRequestToVehicle(req, v, category, sub, branch);
		// Khôi phục hiển thị công khai khi không còn trạng thái Hidden (Hidden vẫn ẩn list công khai)
		if (wasDeleted && !"Hidden".equals(req.getStatus())) {
			v.setDeleted(false);
		}
		v.getImages().clear();
		applyImagesFromRequest(v, req.getImages());

		Vehicle saved = vehicleRepository.save(v);
		evictVehicleCaches(saved.getId());
		return toDetailDto(saved);
	}

	/** Tier 3.3 — Admin duyệt điều chuyển: đánh dấu xe InTransfer tại chi nhánh nguồn (entrypoint Dev 2). */
	@Transactional
	public void applyTransferApprovedMarkInTransfer(long vehicleId, int expectedFromBranchId) {
		Vehicle v = vehicleRepository.findManagedDetailById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		if (v.isDeleted()) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe.");
		}
		if (v.getBranch().getId() != expectedFromBranchId) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_IN_BRANCH, "Xe không thuộc chi nhánh nguồn của yêu cầu.");
		}
		if (!"Available".equals(v.getStatus())) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Xe không ở trạng thái Available để duyệt điều chuyển.");
		}
		v.setStatus("InTransfer");
		vehicleRepository.save(v);
		evictVehicleCaches(vehicleId);
	}

	/** Tier 3.3 — Manager đích xác nhận nhận xe: chuyển branch + Available (entrypoint Dev 2). */
	@Transactional
	public void applyTransferCompleteMoveToBranch(long vehicleId, int fromBranchId, int toBranchId) {
		Vehicle v = vehicleRepository.findManagedDetailById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		if (v.isDeleted()) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe.");
		}
		if (!"InTransfer".equals(v.getStatus())) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Xe không ở trạng thái InTransfer để hoàn tất điều chuyển.");
		}
		if (v.getBranch().getId() != fromBranchId) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_IN_BRANCH, "Xe không còn ở chi nhánh nguồn của yêu cầu.");
		}
		Branch to = branchRepository.findByIdAndDeletedFalse(toBranchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh đích."));
		v.setBranch(to);
		v.setStatus("Available");
		vehicleRepository.save(v);
		evictVehicleCaches(vehicleId);
	}

	@Transactional
	public void softDeleteVehicle(long id, long actorUserId, boolean isAdmin) {
		Vehicle v = vehicleRepository.findManagedDetailById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		if (v.isDeleted()) {
			return;
		}
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		v.setDeleted(true);
		vehicleRepository.save(v);
		evictVehicleCaches(id);
	}

	/** Bỏ cờ xóa mềm — xe hiện lại trên list công khai (trừ khi status = Hidden). */
	@Transactional
	public VehicleDetailDto restorePublicListing(long id, long actorUserId, boolean isAdmin) {
		Vehicle v = vehicleRepository.findManagedDetailById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		if (!v.isDeleted()) {
			return toDetailDto(v);
		}
		v.setDeleted(false);
		vehicleRepository.save(v);
		evictVehicleCaches(id);
		return toDetailDto(v);
	}

	// ===================== SPRINT 4 — STATUS + BULK + IMAGE =====================

	/** Đổi trạng thái xe đơn lẻ (Available, Reserved, Sold, Hidden). */
	@Transactional
	public VehicleDetailDto changeVehicleStatus(long vehicleId, String newStatus, String note,
			long actorUserId, boolean isAdmin) {
		// B1: kiểm tra trạng thái hợp lệ
		if (!VEHICLE_STATUSES.contains(newStatus)) {
			throw new BusinessException(ErrorCode.INVALID_VEHICLE_STATUS,
					"Trạng thái '" + newStatus + "' không hợp lệ.");
		}
		// B2: lấy xe + kiểm quyền chi nhánh
		Vehicle v = vehicleRepository.findManagedDetailById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		// B3: cập nhật trạng thái
		v.setStatus(newStatus);
		vehicleRepository.save(v);
		evictVehicleCaches(vehicleId);
		return toDetailDto(v);
	}

	/** Đổi trạng thái xe hàng loạt — Fail-Fast: nếu bất kỳ xe nào ngoài chi nhánh → 403. */
	@Transactional
	public void bulkChangeStatus(java.util.List<Long> vehicleIds, String newStatus,
			long actorUserId, boolean isAdmin) {
		// B1: kiểm tra input
		if (vehicleIds == null || vehicleIds.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_VEHICLE_LIST, "Danh sách xe rỗng.");
		}
		if (!VEHICLE_STATUSES.contains(newStatus)) {
			throw new BusinessException(ErrorCode.INVALID_VEHICLE_STATUS,
					"Trạng thái '" + newStatus + "' không hợp lệ.");
		}
		// B2: lấy từng xe, kiểm quyền + cập nhật
		for (Long id : vehicleIds) {
			Vehicle v = vehicleRepository.findManagedDetailById(id)
					.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND,
							"Không tìm thấy xe ID=" + id + "."));
			assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
			v.setStatus(newStatus);
			vehicleRepository.save(v);
			evictVehicleCaches(id);
		}
	}

	/** Xóa mềm xe hàng loạt — Fail-Fast: nếu bất kỳ xe nào ngoài chi nhánh → 403. */
	@Transactional
	public void bulkSoftDelete(java.util.List<Long> vehicleIds, long actorUserId, boolean isAdmin) {
		if (vehicleIds == null || vehicleIds.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_VEHICLE_LIST, "Danh sách xe rỗng.");
		}
		for (Long id : vehicleIds) {
			Vehicle v = vehicleRepository.findManagedDetailById(id)
					.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND,
							"Không tìm thấy xe ID=" + id + "."));
			assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
			if (!v.isDeleted()) {
				v.setDeleted(true);
				vehicleRepository.save(v);
				evictVehicleCaches(id);
			}
		}
	}

	/** Thêm ảnh vào xe — ảnh đã upload Cloudinary, chỉ lưu URL vào DB. */
	@Transactional
	public List<VehicleImageDto> addVehicleImages(long vehicleId, List<VehicleImageWriteDto> images,
			long actorUserId, boolean isAdmin) {
		// B1: lấy xe + kiểm quyền
		Vehicle v = vehicleRepository.findManagedDetailById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		// B2: tạo entity ảnh từ DTO
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
		// B3: trả lại danh sách ảnh hiện tại
		return mapVehicleImagesToDtos(v);
	}

	/** Xóa 1 ảnh xe — chỉ xóa record DB (ảnh trên Cloudinary giữ nguyên). */
	@Transactional
	public void deleteVehicleImage(long vehicleId, long imageId, long actorUserId, boolean isAdmin) {
		// B1: lấy xe + kiểm quyền
		Vehicle v = vehicleRepository.findManagedDetailById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		assertCanManageBranch(actorUserId, isAdmin, v.getBranch());
		// B2: tìm ảnh thuộc xe
		VehicleImage img = vehicleImageRepository.findByIdAndVehicle_Id(imageId, vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND, "Không tìm thấy ảnh."));
		// B3: xóa khỏi DB
		v.getImages().remove(img);
		vehicleRepository.save(v);
		evictVehicleCaches(vehicleId);
	}

	/** Giống @CacheEvict: xóa toàn bộ list + 1 key detail. */
	private void evictVehicleCaches(Long vehicleId) {
		Cache list = cacheManager.getCache("vehicleList");
		if (list != null) {
			list.clear();
		}
		Cache detail = cacheManager.getCache("vehicleDetail");
		if (detail != null) {
			detail.evict(detailCacheKey(vehicleId));
		}
	}

	private static String detailCacheKey(long id) {
		return VEHICLE_DETAIL_CACHE_PREFIX + id;
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

	/** Các chi nhánh user được phép thao tác (manager_id + phân công active). */
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

	private static VehicleListResponse buildVehicleListResponse(Page<Vehicle> p) {
		List<VehicleSummaryDto> items = new ArrayList<>();
		for (Vehicle v : p.getContent()) {
			items.add(toSummaryDto(v));
		}
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

	private Subcategory loadSubcategoryForCategory(int subcategoryId, int categoryId) {
		return subcategoryRepository.findByIdAndCategory_Id(subcategoryId, categoryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MODEL_NOT_FOUND,
						"Không tìm thấy dòng xe hoặc không thuộc hãng."));
	}

	private Branch loadBranchAndAssertManager(int branchId, long actorUserId, boolean isAdmin) {
		Branch branch = branchRepository.findActiveByIdWithManager(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
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
	 * Có StaffAssignment active đúng chi nhánh — duyệt list thay vì exists* derived query (tránh edge case Spring Data).
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
		// Truy vấn trực tiếp (phòng branch.manager chưa khớp entity trong session)
		if (branchRepository.findFirstByManager_IdAndDeletedFalse(actorUserId).filter(b -> b.getId().equals(bid))
				.isPresent()) {
			return;
		}
		if (hasActiveAssignmentAtBranch(actorUserId, bid)) {
			return;
		}
		String bname = branch.getName() != null ? branch.getName() : "?";
		throw new BusinessException(ErrorCode.FORBIDDEN, String.format(
				"Không có quyền thao tác xe thuộc chi nhánh \"%s\" (id=%d). Cần: Admin; hoặc tài khoản là manager_id của chi nhánh; hoặc có StaffAssignments đang active tại chi nhánh đó.",
				bname, bid));
	}

	/** Public wrapper — dùng bởi MaintenanceService để kiểm quyền chi nhánh. */
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

	// Sinh chuỗi số ngẫu nhiên (LISTING_ID_DIGITS ký tự), kiểm tra trùng listing_id trong DB — không dùng làm PK.
	private String nextRandomUniqueListingId() {
		for (int attempt = 0; attempt < LISTING_ID_MAX_ATTEMPTS; attempt++) {
			String candidate = randomNumericListingId(LISTING_ID_DIGITS);
			if (!vehicleRepository.existsByListingId(candidate)) {
				return candidate;
			}
		}
		throw new BusinessException(ErrorCode.LISTING_ID_CONFLICT, "Không tạo được mã tin duy nhất, vui lòng thử lại.");
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

}
