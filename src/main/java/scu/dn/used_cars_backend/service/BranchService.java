package scu.dn.used_cars_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.branch.BranchPublicDto;
import scu.dn.used_cars_backend.dto.branch.BranchPublicScheduleDto;
import scu.dn.used_cars_backend.dto.branch.BranchTeamMemberDto;
import scu.dn.used_cars_backend.dto.manager.BranchDayScheduleDto;
import scu.dn.used_cars_backend.dto.manager.BranchDayScheduleItemRequest;
import scu.dn.used_cars_backend.dto.manager.BranchSettingsResponse;
import scu.dn.used_cars_backend.dto.manager.UpdateBranchSettingsRequest;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.BranchWorkingHours;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.StaffAssignment;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.BranchWorkingHoursRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;

import java.time.LocalTime;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class BranchService {

	private static final LocalTime DEFAULT_OPEN = LocalTime.of(8, 0);
	private static final LocalTime DEFAULT_CLOSE = LocalTime.of(18, 0);
	/** Thứ tự “ngày mở đầu tiên” cho tóm tắt open/close (T2 → … → CN). */
	private static final List<Integer> SUMMARY_DAY_ORDER = List.of(1, 2, 3, 4, 5, 6, 0);

	private final BranchRepository branchRepository;
	private final UserRepository userRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final BranchWorkingHoursRepository branchWorkingHoursRepository;
	private final ObjectMapper objectMapper;

	private List<String> readShowroomImageUrls(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			List<String> raw = objectMapper.readValue(json, new TypeReference<List<String>>() {
			});
			return raw.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
		} catch (Exception e) {
			return List.of();
		}
	}

	private void writeShowroomImageUrls(Branch branch, List<String> urls) {
		if (urls == null || urls.isEmpty()) {
			branch.setShowroomImageUrlsJson(null);
			return;
		}
		try {
			branch.setShowroomImageUrlsJson(objectMapper.writeValueAsString(urls));
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không lưu được danh sách ảnh showroom.");
		}
	}

	@Transactional(readOnly = true)
	public List<BranchPublicDto> listPublic() {
		return branchRepository.findAllByDeletedFalseOrderByIdAsc().stream()
				.map(this::toPublicDto)
				.toList();
	}

	@Transactional(readOnly = true)
	public BranchPublicDto getPublicById(int id) {
		Branch b = branchRepository.findByIdAndDeletedFalse(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		return toPublicDto(b);
	}

	/**
	 * Đội ngũ công khai: quản lý (Branches.manager_id) + nhân viên có StaffAssignments active tại chi nhánh.
	 */
	@Transactional(readOnly = true)
	public List<BranchTeamMemberDto> listPublicTeam(int branchId) {
		Branch branch = branchRepository.findActiveByIdWithManager(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));

		List<BranchTeamMemberDto> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();

		User manager = branch.getManager();
		if (manager != null && !Boolean.TRUE.equals(manager.getDeleted())
				&& "active".equalsIgnoreCase(manager.getStatus())) {
			out.add(toTeamMember(manager, "Quản lý chi nhánh"));
			seen.add(manager.getId());
		}

		for (User u : userRepository.findActiveStaffUsersByBranchId(branchId)) {
			if (seen.contains(u.getId())) {
				continue;
			}
			if (!hasSalesOrManagerRole(u)) {
				continue;
			}
			out.add(toTeamMember(u, displayRoleForPublic(u)));
			seen.add(u.getId());
		}

		return out;
	}

	/**
	 * NV bán hàng + QL (active) tại các chi nhánh khác {@code excludeBranchId} — dùng cho QL mở chat liên cửa hàng.
	 */
	@Transactional(readOnly = true)
	public List<BranchTeamMemberDto> listStaffTeamsOutsideBranch(int excludeBranchId) {
		List<BranchTeamMemberDto> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (Branch b : branchRepository.findAllByDeletedFalseOrderByIdAsc()) {
			if (b.getId() == null || b.getId() == excludeBranchId) {
				continue;
			}
			try {
				for (BranchTeamMemberDto m : listPublicTeam(b.getId())) {
					if (m.getUserId() == null || !seen.add(m.getUserId())) {
						continue;
					}
					String baseRole = m.getRole() != null ? m.getRole() : "Nội bộ";
					out.add(BranchTeamMemberDto.builder()
							.userId(m.getUserId())
							.name(m.getName())
							.role(baseRole + " · " + b.getName())
							.avatarUrl(m.getAvatarUrl())
							.build());
				}
			} catch (BusinessException ex) {
				// bỏ qua chi nhánh lỗi cấu hình
			}
		}
		return out;
	}

	/**
	 * Đồng nghiệp cùng chi nhánh (trừ {@code excludeUserId}) + toàn bộ NV/QL active chi nhánh khác (dedupe).
	 */
	@Transactional(readOnly = true)
	public List<BranchTeamMemberDto> listManagerCrossBranchChatContacts(int myBranchId, long excludeUserId) {
		List<BranchTeamMemberDto> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (BranchTeamMemberDto m : listPublicTeam(myBranchId)) {
			if (m.getUserId() == null || m.getUserId().equals(excludeUserId) || !seen.add(m.getUserId())) {
				continue;
			}
			out.add(m);
		}
		for (BranchTeamMemberDto m : listStaffTeamsOutsideBranch(myBranchId)) {
			if (m.getUserId() == null || m.getUserId().equals(excludeUserId) || !seen.add(m.getUserId())) {
				continue;
			}
			out.add(m);
		}
		out.sort(Comparator.comparing(BranchTeamMemberDto::getName, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	/**
	 * Quản lý chi nhánh tại các chi nhánh khác {@code excludeBranchId}.
	 * Ưu tiên cột {@code Branches.manager_id}; nếu null thì tìm user có role BranchManager trong
	 * {@code StaffAssignments} active tại chi nhánh đó (đúng với nhiều bộ seed).
	 */
	@Transactional(readOnly = true)
	public List<BranchTeamMemberDto> listManagersOfOtherBranches(int excludeBranchId) {
		List<BranchTeamMemberDto> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (Branch b : branchRepository.findAllByDeletedFalseOrderByIdAsc()) {
			if (b.getId() == null || b.getId() == excludeBranchId) {
				continue;
			}
			Optional<Long> mgrId = resolveBranchManagerUserId(b);
			if (mgrId.isEmpty()) {
				continue;
			}
			User mgr = userRepository.findActiveByIdWithRoles(mgrId.get()).orElse(null);
			if (mgr == null || Boolean.TRUE.equals(mgr.getDeleted())) {
				continue;
			}
			if (mgr.getStatus() == null || !"active".equalsIgnoreCase(mgr.getStatus().trim())) {
				continue;
			}
			if (!hasBranchManagerRole(mgr)) {
				continue;
			}
			if (!seen.add(mgr.getId())) {
				continue;
			}
			out.add(BranchTeamMemberDto.builder()
					.userId(mgr.getId())
					.name(mgr.getName())
					.role("Quản lý chi nhánh — " + b.getName())
					.avatarUrl(blankToNull(mgr.getAvatarUrl()))
					.build());
		}
		return out;
	}

	/**
	 * 1) {@code Branches.manager_id} nếu user đó còn active và có role BranchManager.
	 * 2) Ngược lại: StaffAssignments hiệu lực tại chi nhánh + role BranchManager.
	 */
	private Optional<Long> resolveBranchManagerUserId(Branch b) {
		User linked = b.getManager();
		if (linked != null && linked.getId() != null) {
			User loaded = userRepository.findActiveByIdWithRoles(linked.getId()).orElse(null);
			if (loaded != null && !Boolean.TRUE.equals(loaded.getDeleted()) && hasBranchManagerRole(loaded)) {
				return Optional.of(loaded.getId());
			}
		}
		for (StaffAssignment sa : staffAssignmentRepository.findCurrentlyActiveByBranchIdOrderByIdDesc(b.getId())) {
			User u = userRepository.findActiveByIdWithRoles(sa.getUserId()).orElse(null);
			if (u != null && !Boolean.TRUE.equals(u.getDeleted()) && hasBranchManagerRole(u)) {
				return Optional.of(u.getId());
			}
		}
		return Optional.empty();
	}

	private static boolean hasBranchManagerRole(User u) {
		if (u.getUserRoles() == null) {
			return false;
		}
		return u.getUserRoles().stream()
				.anyMatch(ur -> ur.getRole() != null && "BranchManager".equals(ur.getRole().getName()));
	}

	private static boolean hasSalesOrManagerRole(User u) {
		return u.getUserRoles().stream().map(ur -> ur.getRole().getName()).anyMatch(BranchService::isSalesOrManagerRoleName);
	}

	private static boolean isSalesOrManagerRoleName(String roleName) {
		return "SalesStaff".equals(roleName) || "BranchManager".equals(roleName);
	}

	private static String displayRoleForPublic(User u) {
		return u.getUserRoles().stream()
				.filter(ur -> isSalesOrManagerRoleName(ur.getRole().getName()))
				.min(Comparator.comparingInt(ur -> ur.getRole().getId()))
				.map(ur -> mapRoleNameToVietnamese(ur.getRole().getName()))
				.orElse("Nhân viên");
	}

	private static String mapRoleNameToVietnamese(String roleName) {
		if (roleName == null) {
			return "Nhân viên";
		}
		return switch (roleName) {
			case "BranchManager" -> "Quản lý chi nhánh";
			case "SalesStaff" -> "Tư vấn viên";
			default -> "Nhân viên";
		};
	}

	private static BranchTeamMemberDto toTeamMember(User u, String roleLabel) {
		return BranchTeamMemberDto.builder()
				.userId(u.getId())
				.name(u.getName())
				.role(roleLabel)
				.avatarUrl(blankToNull(u.getAvatarUrl()))
				.build();
	}

	private static String blankToNull(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		return s.trim();
	}

	private BranchPublicDto toPublicDto(Branch b) {
		int bid = b.getId();
		String st = b.getStatus();
		if (st == null || st.isBlank()) {
			st = "active";
		}
		return BranchPublicDto.builder()
				.id(bid)
				.name(b.getName())
				.address(b.getAddress())
				.phone(b.getPhone())
				.status(st)
				.lat(b.getLat())
				.lng(b.getLng())
				.showroomImageUrls(new ArrayList<>(readShowroomImageUrls(b.getShowroomImageUrlsJson())))
				.workingHours(buildPublicWorkingHours(bid))
				.build();
	}

	private List<BranchPublicScheduleDto> buildPublicWorkingHours(int branchId) {
		List<BranchWorkingHours> rows = branchWorkingHoursRepository.findByBranch_IdOrderByDayOfWeekAsc(branchId);
		Map<Integer, BranchWorkingHours> byDow = new HashMap<>();
		for (BranchWorkingHours h : rows) {
			byDow.put(h.getDayOfWeek(), h);
		}
		List<BranchPublicScheduleDto> out = new ArrayList<>();
		for (int dow = 0; dow <= 6; dow++) {
			BranchWorkingHours h = byDow.get(dow);
			if (h == null) {
				out.add(BranchPublicScheduleDto.builder()
						.dayOfWeek(dow)
						.closed(true)
						.openTime(DEFAULT_OPEN)
						.closeTime(DEFAULT_CLOSE)
						.build());
			} else {
				out.add(BranchPublicScheduleDto.builder()
						.dayOfWeek(dow)
						.closed(h.isClosed())
						.openTime(h.getOpenTime())
						.closeTime(h.getCloseTime())
						.build());
			}
		}
		return out;
	}

	@Transactional(readOnly = true)
	public BranchSettingsResponse getBranchSettings(int branchId) {
		Branch branch = branchRepository.findActiveByIdWithManager(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		List<BranchWorkingHours> rows = branchWorkingHoursRepository.findByBranch_IdOrderByDayOfWeekAsc(branchId);
		Map<Integer, BranchWorkingHours> byDow = new HashMap<>();
		for (BranchWorkingHours h : rows) {
			byDow.put(h.getDayOfWeek(), h);
		}
		List<BranchDayScheduleDto> dailySchedules = new ArrayList<>();
		for (int dow = 0; dow <= 6; dow++) {
			BranchWorkingHours h = byDow.get(dow);
			if (h == null) {
				dailySchedules.add(BranchDayScheduleDto.builder()
						.dayOfWeek(dow)
						.closed(true)
						.openTime(DEFAULT_OPEN)
						.closeTime(DEFAULT_CLOSE)
						.build());
			} else {
				dailySchedules.add(toScheduleDto(h));
			}
		}
		List<Integer> workingDays = dailySchedules.stream()
				.filter(d -> !d.isClosed())
				.map(BranchDayScheduleDto::getDayOfWeek)
				.sorted()
				.toList();
		LocalTime openTime = null;
		LocalTime closeTime = null;
		for (int dow : SUMMARY_DAY_ORDER) {
			BranchDayScheduleDto d = dailySchedules.get(dow);
			if (!d.isClosed()) {
				openTime = d.getOpenTime();
				closeTime = d.getCloseTime();
				break;
			}
		}
		String managerName = null;
		if (branch.getManager() != null) {
			managerName = branch.getManager().getName();
		}
		return BranchSettingsResponse.builder()
				.name(branch.getName())
				.address(branch.getAddress())
				.phone(branch.getPhone())
				.manager(managerName)
				.openTime(openTime)
				.closeTime(closeTime)
				.workingDays(workingDays)
				.dailySchedules(dailySchedules)
				.showroomImageUrls(readShowroomImageUrls(branch.getShowroomImageUrlsJson()))
				.build();
	}

	@Transactional
	public void updateBranchSettings(int branchId, UpdateBranchSettingsRequest request) {
		Set<Integer> expected = IntStream.rangeClosed(0, 6).boxed().collect(Collectors.toSet());
		Set<Integer> seen = new HashSet<>();
		for (BranchDayScheduleItemRequest item : request.getDailySchedules()) {
			if (!seen.add(item.getDayOfWeek())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Trùng day_of_week trong dailySchedules.");
			}
			if (Boolean.FALSE.equals(item.getClosed())) {
				if (item.getOpenTime() == null || item.getCloseTime() == null) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED,
							"Ngày mở cửa phải có giờ mở và giờ đóng.");
				}
				if (item.getOpenTime().equals(item.getCloseTime())) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED,
							"Giờ mở và giờ đóng không được trùng nhau (kể cả ca đêm: hãy chỉnh ít nhất vài phút).");
				}
			}
		}
		if (!seen.equals(expected)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "dailySchedules phải gồm đủ day_of_week 0…6.");
		}
		Branch branch = branchRepository.findByIdAndDeletedFalse(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		branch.setName(request.getName().trim());
		branch.setAddress(request.getAddress().trim());
		branch.setPhone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null);
		if (request.getShowroomImageUrls() != null) {
			writeShowroomImageUrls(branch, request.getShowroomImageUrls());
		}
		branchRepository.save(branch);
		Map<Integer, BranchDayScheduleItemRequest> byDow = request.getDailySchedules().stream()
				.collect(Collectors.toMap(BranchDayScheduleItemRequest::getDayOfWeek, x -> x));
		for (int dow = 0; dow <= 6; dow++) {
			final int dayOfWeek = dow;
			BranchDayScheduleItemRequest item = byDow.get(dayOfWeek);
			BranchWorkingHours row = branchWorkingHoursRepository.findByBranch_IdAndDayOfWeek(branchId, dayOfWeek)
					.orElseGet(() -> {
						BranchWorkingHours h = new BranchWorkingHours();
						h.setBranch(branch);
						h.setDayOfWeek(dayOfWeek);
						return h;
					});
			LocalTime o = item.getOpenTime() != null ? item.getOpenTime() : DEFAULT_OPEN;
			LocalTime c = item.getCloseTime() != null ? item.getCloseTime() : DEFAULT_CLOSE;
			row.setOpenTime(o);
			row.setCloseTime(c);
			row.setClosed(Boolean.TRUE.equals(item.getClosed()));
			branchWorkingHoursRepository.save(row);
		}
	}

	private static BranchDayScheduleDto toScheduleDto(BranchWorkingHours h) {
		return BranchDayScheduleDto.builder()
				.dayOfWeek(h.getDayOfWeek())
				.closed(h.isClosed())
				.openTime(h.getOpenTime())
				.closeTime(h.getCloseTime())
				.build();
	}

}
