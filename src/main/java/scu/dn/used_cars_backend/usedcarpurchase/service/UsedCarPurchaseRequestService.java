package scu.dn.used_cars_backend.usedcarpurchase.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.pricing.ManagerPricingImageAssetRequest;
import scu.dn.used_cars_backend.dto.pricing.ManagerPricingVehicleInputRequest;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.Category;
import scu.dn.used_cars_backend.entity.Subcategory;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleImage;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.CategoryRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.SubcategoryRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestActionRequest;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestCreateRequest;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestListResponse;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestRejectRequest;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestResponse;
import scu.dn.used_cars_backend.usedcarpurchase.entity.UsedCarPurchaseRequest;
import scu.dn.used_cars_backend.usedcarpurchase.repository.UsedCarPurchaseRequestRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UsedCarPurchaseRequestService {

	private static final int LISTING_ID_DIGITS = 12;
	private static final int LISTING_ID_MAX_ATTEMPTS = 20;
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
	private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};

	private final UsedCarPurchaseRequestRepository repository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final BranchRepository branchRepository;
	private final CategoryRepository categoryRepository;
	private final SubcategoryRepository subcategoryRepository;
	private final VehicleRepository vehicleRepository;
	private final ObjectMapper objectMapper;
	private final SecureRandom random = new SecureRandom();

	@Transactional
	public UsedCarPurchaseRequestResponse create(UsedCarPurchaseRequestCreateRequest request, Authentication authentication) {
		long userId = requireUserId(authentication);
		int managerBranchId = requireManagerBranchId(userId);
		if (!Integer.valueOf(managerBranchId).equals(request.getBranchId())) {
			throw new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_ACCESS_DENIED,
					"Chi nhanh gui duyet khong khop voi manager hien tai.");
		}
		validateVehicleSnapshot(request.getVehicleInput(), request.getBranchId());

		UsedCarPurchaseRequest entity = new UsedCarPurchaseRequest();
		entity.setBranchId(request.getBranchId());
		entity.setRequestedBy(userId);
		entity.setRequestedByName(authentication != null ? authentication.getName() : null);
		entity.setStatus("PendingApproval");
		entity.setRequestedPurchasePrice(request.getRequestedPurchasePrice());
		entity.setManagerNote(trimToNull(request.getManagerNote()));
		entity.setVehicleSnapshotJson(toJson(objectMapper.convertValue(request.getVehicleInput(), MAP_TYPE)));
		entity.setImageSnapshotJson(toJson(objectMapper.convertValue(request.getImageAssets(), LIST_MAP_TYPE)));
		entity.setValuationSnapshotJson(toJson(request.getValuationSnapshot()));

		UsedCarPurchaseRequest saved = repository.save(entity);
		return toResponse(saved, true);
	}

	@Transactional(readOnly = true)
	public UsedCarPurchaseRequestListResponse listForManager(Authentication authentication, String status, int page, int size) {
		long userId = requireUserId(authentication);
		int branchId = requireManagerBranchId(userId);
		Page<UsedCarPurchaseRequest> result = hasText(status)
				? repository.findByBranchIdAndStatus(branchId, status.trim(), pageRequest(page, size))
				: repository.findByBranchId(branchId, pageRequest(page, size));
		return toListResponse(result);
	}

	@Transactional(readOnly = true)
	public UsedCarPurchaseRequestResponse getForManager(long id, Authentication authentication) {
		long userId = requireUserId(authentication);
		int branchId = requireManagerBranchId(userId);
		UsedCarPurchaseRequest entity = repository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_NOT_FOUND, "Khong tim thay phieu mua xe cu."));
		if (!Integer.valueOf(branchId).equals(entity.getBranchId())) {
			throw new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_ACCESS_DENIED, "Khong duoc xem phieu cua chi nhanh khac.");
		}
		return toResponse(entity, true);
	}

	@Transactional
	public UsedCarPurchaseRequestResponse markPaid(long id, Authentication authentication) {
		long userId = requireUserId(authentication);
		int branchId = requireManagerBranchId(userId);
		UsedCarPurchaseRequest entity = repository.findByIdForUpdate(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_NOT_FOUND, "Khong tim thay phieu mua xe cu."));
		if (!Integer.valueOf(branchId).equals(entity.getBranchId())) {
			throw new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_ACCESS_DENIED, "Khong duoc thanh toan phieu cua chi nhanh khac.");
		}
		if (!"Approved".equals(entity.getStatus())) {
			throw new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_INVALID_STATUS, "Chi phieu da duoc duyet moi duoc xac nhan da thanh toan.");
		}
		if (entity.getCreatedVehicleId() != null) {
			entity.setStatus("ConvertedToInventory");
			entity.setPaidBy(userId);
			entity.setPaidByName(authentication != null ? authentication.getName() : null);
			if (entity.getPaidAt() == null) {
				entity.setPaidAt(Instant.now());
			}
			return toResponse(repository.save(entity), true);
		}

		Vehicle createdVehicle = createInventoryVehicle(entity, userId);
		entity.setPaidBy(userId);
		entity.setPaidByName(authentication != null ? authentication.getName() : null);
		entity.setPaidAt(Instant.now());
		entity.setCreatedVehicleId(createdVehicle.getId());
		entity.setStatus("ConvertedToInventory");
		return toResponse(repository.save(entity), true);
	}

	@Transactional(readOnly = true)
	public UsedCarPurchaseRequestListResponse listForAdmin(String status, int page, int size) {
		Page<UsedCarPurchaseRequest> result = hasText(status)
				? repository.findAllByStatus(status.trim(), pageRequest(page, size))
				: repository.findAll(pageRequest(page, size));
		return toListResponse(result);
	}

	@Transactional(readOnly = true)
	public UsedCarPurchaseRequestResponse getForAdmin(long id) {
		UsedCarPurchaseRequest entity = repository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_NOT_FOUND, "Khong tim thay phieu mua xe cu."));
		return toResponse(entity, true);
	}

	@Transactional
	public UsedCarPurchaseRequestResponse approve(long id, UsedCarPurchaseRequestActionRequest request, Authentication authentication) {
		UsedCarPurchaseRequest entity = repository.findByIdForUpdate(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_NOT_FOUND, "Khong tim thay phieu mua xe cu."));
		if (!"PendingApproval".equals(entity.getStatus())) {
			throw new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_INVALID_STATUS, "Chi phieu PendingApproval moi duoc duyet.");
		}
		entity.setStatus("Approved");
		entity.setApprovedPurchasePrice(request.getApprovedPurchasePrice());
		entity.setAdminNote(trimToNull(request.getAdminNote()));
		entity.setApprovedBy(requireUserId(authentication));
		entity.setApprovedByName(authentication != null ? authentication.getName() : null);
		entity.setApprovedAt(Instant.now());
		return toResponse(repository.save(entity), true);
	}

	@Transactional
	public UsedCarPurchaseRequestResponse reject(long id, UsedCarPurchaseRequestRejectRequest request, Authentication authentication) {
		UsedCarPurchaseRequest entity = repository.findByIdForUpdate(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_NOT_FOUND, "Khong tim thay phieu mua xe cu."));
		if (!"PendingApproval".equals(entity.getStatus())) {
			throw new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_INVALID_STATUS, "Chi phieu PendingApproval moi duoc tu choi.");
		}
		entity.setStatus("Rejected");
		entity.setAdminNote(request.getAdminNote().trim());
		entity.setApprovedBy(requireUserId(authentication));
		entity.setApprovedByName(authentication != null ? authentication.getName() : null);
		entity.setApprovedAt(Instant.now());
		return toResponse(repository.save(entity), true);
	}

	private UsedCarPurchaseRequestListResponse toListResponse(Page<UsedCarPurchaseRequest> page) {
		UsedCarPurchaseRequestListResponse response = new UsedCarPurchaseRequestListResponse();
		List<UsedCarPurchaseRequestResponse> items = new ArrayList<>();
		for (UsedCarPurchaseRequest entity : page.getContent()) {
			items.add(toResponse(entity, false));
		}
		PageMetaDto meta = new PageMetaDto();
		meta.setPage(page.getNumber());
		meta.setSize(page.getSize());
		meta.setTotalElements(page.getTotalElements());
		meta.setTotalPages(page.getTotalPages());
		response.setItems(items);
		response.setMeta(meta);
		return response;
	}

	private UsedCarPurchaseRequestResponse toResponse(UsedCarPurchaseRequest entity, boolean includeSnapshots) {
		Map<String, Object> vehicleSnapshot = parseMap(entity.getVehicleSnapshotJson());
		List<Map<String, Object>> imageSnapshot = parseListMap(entity.getImageSnapshotJson());
		Map<String, Object> valuationSnapshot = includeSnapshots ? parseMap(entity.getValuationSnapshotJson()) : null;
		String title = vehicleSnapshot != null ? asString(vehicleSnapshot.get("title")) : null;
		String primaryImageUrl = null;
		if (imageSnapshot != null) {
			for (Map<String, Object> row : imageSnapshot) {
				String url = asString(row.get("url"));
				if (url != null && !url.isBlank()) {
					primaryImageUrl = url;
					break;
				}
			}
		}
		return UsedCarPurchaseRequestResponse.builder()
				.id(entity.getId())
				.branchId(entity.getBranchId())
				.requestedBy(entity.getRequestedBy())
				.requestedByName(entity.getRequestedByName())
				.status(entity.getStatus())
				.requestedPurchasePrice(entity.getRequestedPurchasePrice())
				.approvedPurchasePrice(entity.getApprovedPurchasePrice())
				.managerNote(entity.getManagerNote())
				.adminNote(entity.getAdminNote())
				.approvedBy(entity.getApprovedBy())
				.approvedByName(entity.getApprovedByName())
				.approvedAt(entity.getApprovedAt())
				.paidBy(entity.getPaidBy())
				.paidByName(entity.getPaidByName())
				.paidAt(entity.getPaidAt())
				.createdVehicleId(entity.getCreatedVehicleId())
				.createdAt(entity.getCreatedAt())
				.updatedAt(entity.getUpdatedAt())
				.vehicleTitle(title)
				.primaryImageUrl(primaryImageUrl)
				.vehicleSnapshot(includeSnapshots ? vehicleSnapshot : null)
				.imageSnapshot(includeSnapshots ? imageSnapshot : null)
				.valuationSnapshot(valuationSnapshot)
				.build();
	}

	private Vehicle createInventoryVehicle(UsedCarPurchaseRequest entity, long actorUserId) {
		Map<String, Object> vehicleSnapshot = parseMap(entity.getVehicleSnapshotJson());
		List<Map<String, Object>> imageSnapshot = parseListMap(entity.getImageSnapshotJson());
		ManagerPricingVehicleInputRequest input = objectMapper.convertValue(vehicleSnapshot, ManagerPricingVehicleInputRequest.class);
		List<ManagerPricingImageAssetRequest> images = objectMapper.convertValue(imageSnapshot, new TypeReference<>() {});
		validateVehicleSnapshot(input, entity.getBranchId());

		Category category = categoryRepository.findById(input.getCategoryId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND, "Khong tim thay hang xe."));
		Subcategory subcategory = subcategoryRepository.findByIdAndCategory_Id(input.getSubcategoryId(), input.getCategoryId())
				.orElseThrow(() -> new BusinessException(ErrorCode.MODEL_NOT_FOUND, "Khong tim thay dong xe hoac khong thuoc hang."));
		Branch branch = branchRepository.findByIdAndDeletedFalse(entity.getBranchId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Khong tim thay chi nhanh."));

		Vehicle vehicle = new Vehicle();
		vehicle.setListingId(nextRandomUniqueListingId());
		vehicle.setCategory(category);
		vehicle.setSubcategory(subcategory);
		vehicle.setBranch(branch);
		vehicle.setTitle(input.getTitle().trim());
		vehicle.setPrice(entity.getApprovedPurchasePrice() != null ? entity.getApprovedPurchasePrice() : entity.getRequestedPurchasePrice());
		vehicle.setDescription(input.getDescription());
		vehicle.setYear(input.getYear());
		vehicle.setFuel(input.getFuel());
		vehicle.setTransmission(input.getTransmission());
		vehicle.setMileage(input.getMileage() != null ? input.getMileage() : 0);
		vehicle.setBodyStyle(input.getBodyStyle());
		vehicle.setOrigin(input.getOrigin());
		vehicle.setPostingDate(LocalDate.now());
		vehicle.setStatus("Hidden");
		vehicle.setDeleted(false);
		vehicle.setCreatedBy(actorUserId);

		int sortOrder = 0;
		for (ManagerPricingImageAssetRequest image : images) {
			if (image.getUrl() == null || image.getUrl().isBlank()) {
				continue;
			}
			VehicleImage row = new VehicleImage();
			row.setVehicle(vehicle);
			row.setImageUrl(image.getUrl().trim());
			row.setSortOrder(sortOrder);
			row.setPrimaryImage(sortOrder == 0);
			vehicle.getImages().add(row);
			sortOrder++;
		}
		return vehicleRepository.save(vehicle);
	}

	private void validateVehicleSnapshot(ManagerPricingVehicleInputRequest input, Integer branchId) {
		if (input == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thieu vehicle snapshot.");
		}
		if (branchId == null || branchId <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Branch snapshot khong hop le.");
		}
		categoryRepository.findById(input.getCategoryId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND, "Khong tim thay hang xe."));
		subcategoryRepository.findByIdAndCategory_Id(input.getSubcategoryId(), input.getCategoryId())
				.orElseThrow(() -> new BusinessException(ErrorCode.MODEL_NOT_FOUND, "Khong tim thay dong xe hoac khong thuoc hang."));
		branchRepository.findByIdAndDeletedFalse(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Khong tim thay chi nhanh."));
	}

	private int requireManagerBranchId(long userId) {
		return staffAssignmentRepository.findFirstByUserIdAndActiveTrueOrderByIdDesc(userId)
				.map(sa -> sa.getBranchId())
				.or(() -> branchRepository.findFirstByManager_IdAndDeletedFalse(userId).map(Branch::getId))
				.orElseThrow(() -> new BusinessException(ErrorCode.USED_CAR_PURCHASE_REQUEST_ACCESS_DENIED,
						"Khong xac dinh duoc chi nhanh quan ly cua manager."));
	}

	private PageRequest pageRequest(int page, int size) {
		return PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt"));
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Khong the dong goi snapshot gui duyet.");
		}
	}

	private Map<String, Object> parseMap(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(json, MAP_TYPE);
		} catch (Exception ex) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Khong doc duoc snapshot xe.");
		}
	}

	private List<Map<String, Object>> parseListMap(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, LIST_MAP_TYPE);
		} catch (Exception ex) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Khong doc duoc snapshot anh.");
		}
	}

	private long requireUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getDetails() instanceof Long userId)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yeu cau dang nhap.");
		}
		return userId;
	}

	private String nextRandomUniqueListingId() {
		for (int attempt = 0; attempt < LISTING_ID_MAX_ATTEMPTS; attempt++) {
			String candidate = randomNumericListingId(LISTING_ID_DIGITS);
			if (!vehicleRepository.existsByListingId(candidate)) {
				return candidate;
			}
		}
		throw new BusinessException(ErrorCode.LISTING_ID_CONFLICT, "Khong tao duoc ma tin duy nhat cho xe da mua.");
	}

	private String randomNumericListingId(int digits) {
		StringBuilder sb = new StringBuilder(digits);
		sb.append(1 + random.nextInt(9));
		for (int i = 1; i < digits; i++) {
			sb.append(random.nextInt(10));
		}
		return sb.toString();
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}
}
