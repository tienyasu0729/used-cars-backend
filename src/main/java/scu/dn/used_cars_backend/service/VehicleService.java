package scu.dn.used_cars_backend.service;

// Service xử lý logic xe: danh sách/chi tiết công khai, lưu xe cho khách, tạo-sửa-xóa cho manager.
// Map DTO thủ công trong service (không dùng MapStruct). Cache đọc/ghi bằng CacheManager cho dễ hiểu.

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.security.access.AccessDeniedException;
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
import scu.dn.used_cars_backend.entity.Subcategory;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleImage;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.CategoryRepository;
import scu.dn.used_cars_backend.repository.SubcategoryRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class VehicleService {

	private static final Set<String> VEHICLE_STATUSES = Set.of("Available", "Reserved", "Sold", "Hidden", "InTransfer");
	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
	/** Tiền tố khóa cache — đổi khi DTO list/detail thay đổi để tránh trả bản cũ thiếu field. */
	private static final String VEHICLE_LIST_CACHE_PREFIX = "v3:";
	private static final String VEHICLE_DETAIL_CACHE_PREFIX = "v3:";

	private final VehicleRepository vehicleRepository;
	private final CategoryRepository categoryRepository;
	private final SubcategoryRepository subcategoryRepository;
	private final BranchRepository branchRepository;
	private final UserRepository userRepository;
	private final CacheManager cacheManager;

	public VehicleService(VehicleRepository vehicleRepository, CategoryRepository categoryRepository,
			SubcategoryRepository subcategoryRepository, BranchRepository branchRepository, UserRepository userRepository,
			CacheManager cacheManager) {
		this.vehicleRepository = vehicleRepository;
		this.categoryRepository = categoryRepository;
		this.subcategoryRepository = subcategoryRepository;
		this.branchRepository = branchRepository;
		this.userRepository = userRepository;
		this.cacheManager = cacheManager;
	}

	@Transactional(readOnly = true)
	public VehicleListResponse listPublic(Integer categoryId, BigDecimal minPrice, BigDecimal maxPrice, int page, int size,
			String sort) {
		String sortKey = normalizeListSortKey(sort);
		// B1: khóa cache gồm cả sort (idDesc | postingDateDesc)
		String key = buildListCacheKey(categoryId, minPrice, maxPrice, page, size, sortKey);
		Cache cache = cacheManager.getCache("vehicleList");
		if (cache != null) {
			Cache.ValueWrapper w = cache.get(key);
			if (w != null && w.get() != null) {
				return (VehicleListResponse) w.get();
			}
		}
		// B2: không có cache → query DB + map DTO
		VehicleListResponse body = loadListFromDatabase(categoryId, minPrice, maxPrice, page, size, sortKey);
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

	@Transactional
	public VehicleDetailDto createVehicle(VehicleCreateRequest req, long actorUserId, boolean isAdmin) {
		// B1: khóa category + load subcategory + branch và kiểm tra quyền
		Category category = categoryRepository.findByIdForUpdate(req.getCategoryId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND, "Không tìm thấy hãng."));
		Subcategory sub = loadSubcategoryForCategory(req.getSubcategoryId(), req.getCategoryId());
		Branch branch = loadBranchAndAssertManager(req.getBranchId(), actorUserId, isAdmin);

		// B2: tạo entity + listing_id + ảnh
		Vehicle v = new Vehicle();
		v.setListingId(nextListingId(category, req.getYear()));
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
		// B1: lấy xe + kiểm tra xóa mềm + quyền trên chi nhánh hiện tại
		Vehicle v = vehicleRepository.findManagedDetailById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		if (v.isDeleted()) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe.");
		}
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
		copyUpdateRequestToVehicle(req, v, category, sub, branch);
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
			return "idDesc";
		}
		String s = sort.trim();
		if ("postingDateDesc".equalsIgnoreCase(s) || "posting_date_desc".equalsIgnoreCase(s)) {
			return "postingDateDesc";
		}
		return "idDesc";
	}

	private static Sort listSortForPublicList(String sortKey) {
		if ("postingDateDesc".equals(sortKey)) {
			return Sort.by(Order.desc("postingDate").with(Sort.NullHandling.NULLS_LAST), Order.desc("id"));
		}
		return Sort.by(Order.desc("id"));
	}

	private static String buildListCacheKey(Integer categoryId, BigDecimal minPrice, BigDecimal maxPrice, int page,
			int size, String sortKey) {
		String c = categoryId != null ? String.valueOf(categoryId) : "all";
		String min = minPrice != null ? minPrice.toString() : "x";
		String max = maxPrice != null ? maxPrice.toString() : "x";
		return VEHICLE_LIST_CACHE_PREFIX + c + "|" + min + "|" + max + "|" + page + "|" + size + "|" + sortKey;
	}

	private VehicleListResponse loadListFromDatabase(Integer categoryId, BigDecimal minPrice, BigDecimal maxPrice,
			int page, int size, String sortKey) {
		Sort sort = listSortForPublicList(sortKey);
		PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), sort);
		Page<Vehicle> p = vehicleRepository.findPublicPage(categoryId, minPrice, maxPrice, pr);
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

	private void assertCanManageBranch(long actorUserId, boolean isAdmin, Branch branch) {
		if (isAdmin) {
			return;
		}
		User manager = branch.getManager();
		if (manager == null || !Objects.equals(manager.getId(), actorUserId)) {
			throw new AccessDeniedException("Chỉ Admin hoặc BranchManager của chi nhánh này được thao tác.");
		}
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

	// B1: ghép prefix BRAND+YY — B2: tìm SEQ max — B3: trả mã mới
	private String nextListingId(Category category, Integer modelYear) {
		String prefix = buildListingIdPrefix(category, modelYear);
		int maxSeq = maxSeqForListingPrefix(prefix);
		int next = maxSeq + 1;
		if (next > 9999) {
			throw new IllegalStateException("Hết dải SEQ cho prefix " + prefix);
		}
		return prefix + String.format(Locale.ROOT, "%04d", next);
	}

	private String buildListingIdPrefix(Category category, Integer modelYear) {
		String brand = brandPrefixFromCategoryName(category.getName());
		int y = modelYear != null ? modelYear : Year.now(VN).getValue();
		String yy = String.format(Locale.ROOT, "%02d", y % 100);
		return brand + yy;
	}

	private int maxSeqForListingPrefix(String prefix) {
		List<String> existing = vehicleRepository.findListingIdsByPrefix(prefix);
		int maxSeq = 0;
		for (String listingId : existing) {
			if (listingId.length() < prefix.length() + 4) {
				continue;
			}
			try {
				int seq = Integer.parseInt(listingId.substring(prefix.length(), prefix.length() + 4));
				if (seq > maxSeq) {
					maxSeq = seq;
				}
			}
			catch (NumberFormatException ignored) {
				// bỏ qua mã không parse được
			}
		}
		return maxSeq;
	}

	private static String brandPrefixFromCategoryName(String categoryName) {
		if (categoryName == null || categoryName.isBlank()) {
			return "XXX";
		}
		StringBuilder sb = new StringBuilder();
		for (char c : categoryName.toCharArray()) {
			if (Character.isLetter(c)) {
				sb.append(Character.toUpperCase(c));
			}
			if (sb.length() >= 3) {
				break;
			}
		}
		while (sb.length() < 3) {
			sb.append('X');
		}
		return sb.substring(0, 3);
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
		dto.setStatus(v.getStatus());
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
