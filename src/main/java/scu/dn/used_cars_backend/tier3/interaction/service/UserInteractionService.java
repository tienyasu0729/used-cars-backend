package scu.dn.used_cars_backend.tier3.interaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleImage;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.service.vehicle.InteractionVehicleSnapshot;
import scu.dn.used_cars_backend.service.vehicle.VehicleReadPort;
import scu.dn.used_cars_backend.tier3.interaction.dto.MergeViewHistoryDataResponse;
import scu.dn.used_cars_backend.tier3.interaction.dto.MessageDataResponse;
import scu.dn.used_cars_backend.tier3.interaction.dto.SavedVehicleResponse;
import scu.dn.used_cars_backend.tier3.interaction.dto.ViewHistoryResponse;
import scu.dn.used_cars_backend.tier3.interaction.entity.SavedVehicle;
import scu.dn.used_cars_backend.tier3.interaction.entity.SavedVehicleId;
import scu.dn.used_cars_backend.tier3.interaction.entity.VehicleViewHistory;
import scu.dn.used_cars_backend.tier3.interaction.repository.SavedVehicleRepository;
import scu.dn.used_cars_backend.tier3.interaction.repository.VehicleViewHistoryRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserInteractionService {

	private static final int REDIS_LIST_MAX = 50;
	private static final int RECENT_COUNT = 10;
	private static final int DB_FALLBACK_PAGE = 80;

	private final SavedVehicleRepository savedVehicleRepository;
	private final VehicleViewHistoryRepository vehicleViewHistoryRepository;
	private final UserRepository userRepository;
	private final VehicleReadPort vehicleReadPort;
	private final VehicleViewHistoryAsyncWriter vehicleViewHistoryAsyncWriter;
	private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;

	/** Lưu xe yêu thích — chỉ xe tồn tại và chưa xóa mềm (qua VehicleReadPort). */
	@Transactional
	public MessageDataResponse saveVehicle(long userId, long vehicleId) {
		// B1: lấy user hợp lệ
		User user = userRepository.findByIdAndDeletedFalse(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
		// B2: kiểm tra xe tồn tại và chưa xóa mềm
		if (!vehicleReadPort.existsForCustomerSave(vehicleId)) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe.");
		}
		// B3: tránh trùng khóa
		if (savedVehicleRepository.existsByUser_IdAndVehicle_Id(userId, vehicleId)) {
			throw new BusinessException(ErrorCode.VEHICLE_ALREADY_SAVED, "Xe đã được lưu.");
		}
		// B4: lưu bản ghi mới (FK vehicle qua reference Dev 2)
		Vehicle vehicleRef = vehicleReadPort.vehicleFkReference(vehicleId);
		SavedVehicle sv = new SavedVehicle();
		sv.setUser(user);
		sv.setVehicle(vehicleRef);
		sv.setId(new SavedVehicleId(user.getId(), vehicleId));
		savedVehicleRepository.save(sv);
		return new MessageDataResponse("Đã lưu xe vào danh sách yêu thích");
	}

	/** Bỏ lưu — nếu chưa có bản ghi thì VEHICLE_NOT_SAVED. */
	@Transactional
	public MessageDataResponse unsaveVehicle(long userId, long vehicleId) {
		// B1: kiểm tra đã lưu
		SavedVehicleId id = new SavedVehicleId(userId, vehicleId);
		if (!savedVehicleRepository.existsById(id)) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_SAVED, "Xe chưa có trong danh sách đã lưu.");
		}
		// B2: xóa
		savedVehicleRepository.deleteById(id);
		return new MessageDataResponse("Đã bỏ lưu xe");
	}

	/** Danh sách đã lưu, saved_at DESC, kèm ảnh primary từ join. */
	@Transactional(readOnly = true)
	public List<SavedVehicleResponse> listSavedVehicles(long userId) {
		// B1: query đã lưu, sắp xếp saved_at giảm dần
		List<SavedVehicle> rows = savedVehicleRepository.findAllSavedForUser(userId);
		List<SavedVehicleResponse> out = new ArrayList<>();
		for (SavedVehicle sv : rows) {
			Vehicle v = sv.getVehicle();
			out.add(SavedVehicleResponse.builder()
					.vehicleId(v.getId())
					.listingId(v.getListingId())
					.title(v.getTitle())
					.price(v.getPrice())
					.status(v.getStatus())
					.primaryImageUrl(pickPrimaryImageUrl(v))
					.savedAt(sv.getSavedAt())
					.build());
		}
		return out;
	}

	/**
	 * Ghi lượt xem: Redis LPUSH (nếu được) + async INSERT. Không guest header → return sớm, không lỗi.
	 */
	public void recordView(String guestId, Long userId, long vehicleId) {
		// B1: không có guest → bỏ qua im lặng
		if (guestId == null || guestId.isBlank()) {
			return;
		}
		String g = guestId.trim();
		// B2: đẩy Redis (không crash nếu Redis lỗi)
		String redisKey = redisKeyForViewer(g, userId);
		tryPushRedis(redisKey, vehicleId);
		// B3: ghi DB bất đồng bộ
		vehicleViewHistoryAsyncWriter.insertAsync(g, userId, vehicleId);
	}

	/** 10 xe gần nhất: Redis trước, miss thì DB; lọc xe đã xóa / Hidden. */
	@Transactional(readOnly = true)
	public List<ViewHistoryResponse> recentlyViewed(String guestId, Long userId) {
		// B1: ưu tiên user đã đăng nhập
		List<Long> orderedIds = new ArrayList<>();
		String key = redisKeyForViewer(guestId != null ? guestId.trim() : "", userId);
		List<String> fromRedis = tryRangeRedis(key, RECENT_COUNT);
		if (!fromRedis.isEmpty()) {
			for (String s : fromRedis) {
				try {
					orderedIds.add(Long.parseLong(s));
				}
				catch (NumberFormatException ignored) {
					// bỏ qua phần tử không hợp lệ
				}
			}
		}
		if (orderedIds.isEmpty()) {
			orderedIds = loadRecentIdsFromDb(userId, guestId);
		}
		return mapToViewResponses(orderedIds);
	}

	/** Merge guest → user: Redis dedupe + UPDATE history WHERE guest_id và user_id IS NULL. */
	@Transactional
	public MergeViewHistoryDataResponse mergeGuestViewHistory(long userId, String guestId) {
		if (guestId == null || guestId.isBlank()) {
			return new MergeViewHistoryDataResponse(0);
		}
		String g = guestId.trim();
		int redisMerged = 0;
		String guestKey = "view:guest:" + g;
		String userKey = "view:user:" + userId;
		// B1: merge Redis guest → user (tránh trùng)
		try {
			StringRedisTemplate redis = stringRedisTemplateProvider.getIfAvailable();
			if (redis != null) {
				List<String> guestList = redis.opsForList().range(guestKey, 0, -1);
				if (guestList != null && !guestList.isEmpty()) {
					List<String> userList = redis.opsForList().range(userKey, 0, -1);
					Set<String> seen = new HashSet<>();
					if (userList != null) {
						seen.addAll(userList);
					}
					for (String vid : guestList) {
						if (vid != null && seen.add(vid)) {
							redis.opsForList().leftPush(userKey, vid);
							redisMerged++;
						}
					}
					redis.opsForList().trim(userKey, 0, REDIS_LIST_MAX - 1);
				}
				redis.delete(guestKey);
			}
		}
		catch (Exception e) {
			log.warn("Redis merge view history skipped: {}", e.getMessage());
		}
		// B2: cập nhật DB — guest → user
		int dbUpdated = vehicleViewHistoryRepository.updateUserIdForGuestMerge(userId, g);
		return new MergeViewHistoryDataResponse(redisMerged + dbUpdated);
	}

	private List<Long> loadRecentIdsFromDb(Long userId, String guestId) {
		PageRequest page = PageRequest.of(0, DB_FALLBACK_PAGE);
		List<VehicleViewHistory> rows;
		if (userId != null) {
			// B1: lịch sử theo user
			rows = vehicleViewHistoryRepository.findRecentByUserId(userId, page);
		}
		else if (guestId != null && !guestId.isBlank()) {
			// B2: guest chưa gắn user
			rows = vehicleViewHistoryRepository.findRecentByGuestOnly(guestId.trim(), page);
		}
		else {
			return List.of();
		}
		// B3: dedupe vehicle_id, giữ thứ tự viewed_at giảm dần
		List<Long> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (VehicleViewHistory h : rows) {
			if (seen.add(h.getVehicleId())) {
				out.add(h.getVehicleId());
				if (out.size() >= RECENT_COUNT) {
					break;
				}
			}
		}
		return out;
	}

	private List<ViewHistoryResponse> mapToViewResponses(List<Long> orderedIds) {
		if (orderedIds.isEmpty()) {
			return List.of();
		}
		List<InteractionVehicleSnapshot> snaps = vehicleReadPort.findSnapshotsByIdsPreserveOrder(orderedIds);
		var byId = snaps.stream().collect(Collectors.toMap(InteractionVehicleSnapshot::id, s -> s, (a, b) -> a));
		List<ViewHistoryResponse> out = new ArrayList<>();
		for (Long id : orderedIds) {
			InteractionVehicleSnapshot s = byId.get(id);
			if (s == null || s.deleted() || "Hidden".equals(s.status())) {
				continue;
			}
			out.add(ViewHistoryResponse.builder()
					.vehicleId(s.id())
					.listingId(s.listingId())
					.title(s.title())
					.price(s.price())
					.primaryImageUrl(s.primaryImageUrl())
					.build());
			if (out.size() >= RECENT_COUNT) {
				break;
			}
		}
		return out;
	}

	private static String redisKeyForViewer(String guestId, Long userId) {
		if (userId != null) {
			return "view:user:" + userId;
		}
		return "view:guest:" + Objects.requireNonNullElse(guestId, "");
	}

	private void tryPushRedis(String key, long vehicleId) {
		try {
			StringRedisTemplate redis = stringRedisTemplateProvider.getIfAvailable();
			if (redis == null || key.endsWith(":")) {
				return;
			}
			String v = Long.toString(vehicleId);
			// B1: LPUSH + LTRIM giữ tối đa 50 phần tử
			redis.opsForList().leftPush(key, v);
			redis.opsForList().trim(key, 0, REDIS_LIST_MAX - 1);
		}
		catch (Exception e) {
			log.debug("Redis view push skipped: {}", e.getMessage());
		}
	}

	private List<String> tryRangeRedis(String key, int endInclusive) {
		try {
			StringRedisTemplate redis = stringRedisTemplateProvider.getIfAvailable();
			if (redis == null || key.endsWith(":")) {
				return List.of();
			}
			List<String> list = redis.opsForList().range(key, 0, endInclusive);
			return list != null ? list : List.of();
		}
		catch (Exception e) {
			log.debug("Redis view range skipped: {}", e.getMessage());
			return List.of();
		}
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
}
