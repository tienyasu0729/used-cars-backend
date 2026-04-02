package scu.dn.used_cars_backend.service;

// Service quản lý nhân viên chi nhánh (SalesStaff / BranchManager trong phạm vi list) cho Admin & BranchManager.
// Luồng: validate → User / UserRoles / StaffAssignments — không gọi HTTP ở đây.

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.manager.CreateStaffRequest;
import scu.dn.used_cars_backend.dto.manager.StaffAssignmentItemDto;
import scu.dn.used_cars_backend.dto.manager.StaffListItemDto;
import scu.dn.used_cars_backend.dto.manager.TransferStaffRequest;
import scu.dn.used_cars_backend.dto.manager.UpdateStaffRequest;
import scu.dn.used_cars_backend.dto.manager.UpdateStaffStatusRequest;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.Role;
import scu.dn.used_cars_backend.entity.StaffAssignment;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.RoleRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffService {

	private static final String ROLE_SALES = "SalesStaff";
	private static final String ROLE_MANAGER = "BranchManager";
	private static final Set<String> STAFF_ROLE_NAMES = Set.of(ROLE_SALES, ROLE_MANAGER);

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final BranchRepository branchRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final PasswordEncoder passwordEncoder;

	/** Ưu tiên StaffAssignment active, sau đó Branches.manager_id — không có thì 404 BRANCH_NOT_FOUND. */
	public int getManagerBranchId(long userId) {
		return staffAssignmentRepository.findFirstByUserIdAndActiveTrueOrderByIdDesc(userId)
				.map(StaffAssignment::getBranchId)
				.or(() -> branchRepository.findFirstByManager_IdAndDeletedFalse(userId).map(Branch::getId))
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND,
						"Không xác định được chi nhánh quản lý."));
	}

	/**
	 * Admin bắt buộc query {@code branchId}; BranchManager/SalesStaff lấy từ assignment hoặc manager_id.
	 */
	public int resolveBranchIdForAdminOrBranchStaff(Long branchIdParam, long actorUserId, boolean isAdmin) {
		if (isAdmin) {
			if (branchIdParam == null) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Param branchId is required for ADMIN role");
			}
			int bid;
			try {
				bid = Math.toIntExact(branchIdParam);
			} catch (ArithmeticException ex) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "branchId không hợp lệ.");
			}
			branchRepository.findByIdAndDeletedFalse(bid)
					.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
			return bid;
		}
		return getManagerBranchId(actorUserId);
	}

	@Transactional(readOnly = true)
	public List<StaffListItemDto> listStaff(Integer branchIdQuery, long actorUserId, boolean isAdmin) {
		Integer filterBranchId;
		if (isAdmin) {
			filterBranchId = branchIdQuery;
		} else {
			filterBranchId = getManagerBranchId(actorUserId);
		}
		List<User> users = userRepository.findStaffUsersForManagerList(filterBranchId);
		Map<Integer, String> branchNames = loadBranchNameMap();
		Map<Long, StaffAssignment> activeByUserId = new HashMap<>();
		if (!users.isEmpty()) {
			List<Long> ids = users.stream().map(User::getId).toList();
			for (StaffAssignment sa : staffAssignmentRepository.findActiveByUserIdIn(ids)) {
				activeByUserId.putIfAbsent(sa.getUserId(), sa);
			}
		}
		return users.stream()
				.map(u -> toListItem(u, branchNames, activeByUserId.get(u.getId())))
				.collect(Collectors.toList());
	}

	@Transactional
	public StaffListItemDto createStaff(CreateStaffRequest request, long actorUserId, boolean isAdmin) {
		int targetBranchId = request.getBranchId();
		if (!isAdmin) {
			int myBranch = getManagerBranchId(actorUserId);
			if (targetBranchId != myBranch) {
				throw new BusinessException(ErrorCode.STAFF_NOT_IN_BRANCH, "Chỉ tạo nhân viên tại chi nhánh của bạn.");
			}
		}
		branchRepository.findByIdAndDeletedFalse(targetBranchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));

		String email = request.getEmail().trim().toLowerCase();
		if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
			throw new BusinessException(ErrorCode.STAFF_EMAIL_EXISTS, "Email đã được sử dụng.");
		}
		String phone = request.getPhone().trim();
		if (userRepository.existsByPhoneIgnoreCaseAndDeletedFalse(phone)) {
			throw new BusinessException(ErrorCode.STAFF_PHONE_EXISTS, "Số điện thoại đã được sử dụng.");
		}

		Role salesRole = roleRepository.findByName(ROLE_SALES)
				.orElseThrow(() -> new IllegalStateException("Vai trò SalesStaff chưa được seed trong database."));
		User user = new User();
		user.setName(request.getName().trim());
		user.setEmail(email);
		user.setPhone(phone);
		user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
		user.setAuthProvider("local");
		user.setStatus("active");
		user.setDeleted(false);
		UserRole link = new UserRole();
		link.setUser(user);
		link.setRole(salesRole);
		user.getUserRoles().add(link);
		User saved = userRepository.save(user);

		StaffAssignment sa = new StaffAssignment();
		sa.setUserId(saved.getId());
		sa.setBranchId(targetBranchId);
		sa.setStartDate(LocalDate.now());
		sa.setActive(true);
		staffAssignmentRepository.save(sa);

		return toListItem(saved, loadBranchNameMap());
	}

	@Transactional
	public StaffListItemDto updateStaff(long staffId, UpdateStaffRequest request, long actorUserId, boolean isAdmin) {
		User user = loadStaffUser(staffId);
		assertActorCanManageStaff(actorUserId, isAdmin, staffId);
		assertNonAdminCannotMutateSameStaffRole(actorUserId, isAdmin, user);

		user.setName(request.getName().trim());
		String phone = request.getPhone();
		if (phone == null || phone.isBlank()) {
			user.setPhone(null);
		} else {
			String p = phone.trim();
			if (userRepository.existsByPhoneIgnoreCaseAndDeletedFalseAndIdNot(p, staffId)) {
				throw new BusinessException(ErrorCode.STAFF_PHONE_EXISTS, "Số điện thoại đã được sử dụng.");
			}
			user.setPhone(p);
		}
		userRepository.save(user);
		return toListItem(user, loadBranchNameMap());
	}

	@Transactional
	public void updateStaffStatus(long staffId, UpdateStaffStatusRequest request, long actorUserId, boolean isAdmin) {
		User user = loadStaffUser(staffId);
		assertActorCanManageStaff(actorUserId, isAdmin, staffId);
		assertNonAdminCannotMutateSameStaffRole(actorUserId, isAdmin, user);
		user.setStatus(request.getStatus().trim().toLowerCase());
		userRepository.save(user);
	}

	@Transactional
	public void softDeleteStaff(long staffId, long actorUserId, boolean isAdmin) {
		User user = loadStaffUser(staffId);
		assertActorCanManageStaff(actorUserId, isAdmin, staffId);
		assertNonAdminCannotMutateSameStaffRole(actorUserId, isAdmin, user);
		user.setDeleted(true);
		userRepository.save(user);
		LocalDate today = LocalDate.now();
		for (StaffAssignment sa : staffAssignmentRepository.findByUserIdAndActiveTrue(staffId)) {
			sa.setActive(false);
			sa.setEndDate(today);
			staffAssignmentRepository.save(sa);
		}
	}

	@Transactional(readOnly = true)
	public List<StaffAssignmentItemDto> listAssignments(long staffId, long actorUserId, boolean isAdmin) {
		loadStaffUser(staffId);
		assertActorCanManageStaff(actorUserId, isAdmin, staffId);
		Map<Integer, String> branchNames = loadBranchNameMap();
		return staffAssignmentRepository.findByUserIdOrderByStartDateDesc(staffId).stream()
				.map(sa -> StaffAssignmentItemDto.builder()
						.id(sa.getId())
						.branchId(sa.getBranchId())
						.branchName(branchNames.getOrDefault(sa.getBranchId(), ""))
						.startDate(sa.getStartDate())
						.endDate(sa.getEndDate())
						.active(sa.isActive())
						.build())
				.collect(Collectors.toList());
	}

	@Transactional
	public StaffAssignmentItemDto transferStaff(long staffId, TransferStaffRequest request, long actorUserId,
			boolean isAdmin) {
		User target = loadStaffUser(staffId);
		assertActorCanManageStaff(actorUserId, isAdmin, staffId);
		assertNonAdminCannotMutateSameStaffRole(actorUserId, isAdmin, target);

		int newBranchId = request.getBranchId();
		LocalDate startDate = request.getStartDate();
		if (startDate.isBefore(LocalDate.now())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ngày bắt đầu không được trong quá khứ.");
		}
		branchRepository.findByIdAndDeletedFalse(newBranchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh đích."));

		List<StaffAssignment> currentActives = staffAssignmentRepository.findByUserIdAndActiveTrue(staffId);
		if (!currentActives.isEmpty()) {
			if (currentActives.stream().anyMatch(sa -> sa.getBranchId() == newBranchId)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Nhân viên đã đang làm tại chi nhánh này.");
			}
			LocalDate latestStart = currentActives.stream().map(StaffAssignment::getStartDate).max(LocalDate::compareTo)
					.orElseThrow();
			if (!startDate.isAfter(latestStart)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Ngày bắt đầu mới phải sau ngày bắt đầu phân công hiện tại.");
			}
			LocalDate endPrev = startDate.minusDays(1);
			for (StaffAssignment current : currentActives) {
				current.setEndDate(endPrev);
				current.setActive(false);
				staffAssignmentRepository.save(current);
			}
		}

		StaffAssignment next = new StaffAssignment();
		next.setUserId(staffId);
		next.setBranchId(newBranchId);
		next.setStartDate(startDate);
		next.setActive(true);
		StaffAssignment saved = staffAssignmentRepository.save(next);
		Map<Integer, String> branchNames = loadBranchNameMap();
		return StaffAssignmentItemDto.builder()
				.id(saved.getId())
				.branchId(saved.getBranchId())
				.branchName(branchNames.getOrDefault(saved.getBranchId(), ""))
				.startDate(saved.getStartDate())
				.endDate(saved.getEndDate())
				.active(saved.isActive())
				.build();
	}

	private void assertActorCanManageStaff(long actorUserId, boolean isAdmin, long staffUserId) {
		if (isAdmin) {
			return;
		}
		int branchId = getManagerBranchId(actorUserId);
		if (!isUserLinkedToBranch(staffUserId, branchId)) {
			throw new BusinessException(ErrorCode.STAFF_NOT_IN_BRANCH, "Nhân viên không thuộc chi nhánh của bạn.");
		}
	}

	/**
	 * Non-admin: không cho chỉnh / khóa / xóa / điều chuyển nhân sự có cùng vai trò hệ thống (SalesStaff–SalesStaff,
	 * BranchManager–BranchManager). Admin bỏ qua.
	 */
	private void assertNonAdminCannotMutateSameStaffRole(long actorUserId, boolean isAdmin, User targetUser) {
		if (isAdmin) {
			return;
		}
		User actor = userRepository.findActiveByIdWithRoles(actorUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập."));
		String actorRole = resolveStaffRoleName(actor);
		String targetRole = resolveStaffRoleName(targetUser);
		if (actorRole.isEmpty() || targetRole.isEmpty()) {
			return;
		}
		if (actorRole.equals(targetRole)) {
			throw new BusinessException(ErrorCode.STAFF_PEER_EDIT_FORBIDDEN,
					"Không thể chỉnh sửa nhân sự cùng vai trò hệ thống với tài khoản của bạn.");
		}
	}

	private boolean isUserLinkedToBranch(long userId, int branchId) {
		Optional<StaffAssignment> active = staffAssignmentRepository
				.findFirstByUserIdAndActiveTrueOrderByIdDesc(userId);
		if (active.isPresent() && active.get().getBranchId() == branchId) {
			return true;
		}
		return branchRepository.findFirstByManager_IdAndDeletedFalse(userId)
				.map(b -> b.getId() == branchId)
				.orElse(false);
	}

	private User loadStaffUser(long userId) {
		User u = userRepository.findActiveByIdWithRoles(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND, "Không tìm thấy nhân viên."));
		if (!hasStaffRole(u)) {
			throw new BusinessException(ErrorCode.STAFF_NOT_FOUND, "Không tìm thấy nhân viên.");
		}
		return u;
	}

	private static boolean hasStaffRole(User u) {
		return u.getUserRoles().stream().map(ur -> ur.getRole().getName()).anyMatch(STAFF_ROLE_NAMES::contains);
	}

	private StaffListItemDto toListItem(User u, Map<Integer, String> branchNames) {
		StaffAssignment cached = staffAssignmentRepository.findFirstByUserIdAndActiveTrueOrderByIdDesc(u.getId())
				.orElse(null);
		return toListItem(u, branchNames, cached);
	}

	private StaffListItemDto toListItem(User u, Map<Integer, String> branchNames, StaffAssignment activeCached) {
		Integer branchId = null;
		String branchName = null;
		if (activeCached != null) {
			branchId = activeCached.getBranchId();
			branchName = branchNames.get(branchId);
		} else {
			Optional<Branch> managed = branchRepository.findFirstByManager_IdAndDeletedFalse(u.getId());
			if (managed.isPresent()) {
				branchId = managed.get().getId();
				branchName = managed.get().getName();
			}
		}
		return StaffListItemDto.builder()
				.id(u.getId())
				.name(u.getName())
				.email(u.getEmail())
				.phone(u.getPhone())
				.role(resolveStaffRoleName(u))
				.branchId(branchId)
				.branchName(branchName != null ? branchName : "")
				.status(u.getStatus())
				.createdAt(u.getCreatedAt())
				.build();
	}

	private static String resolveStaffRoleName(User u) {
		if (u.getUserRoles().stream().anyMatch(ur -> ROLE_MANAGER.equals(ur.getRole().getName()))) {
			return ROLE_MANAGER;
		}
		if (u.getUserRoles().stream().anyMatch(ur -> ROLE_SALES.equals(ur.getRole().getName()))) {
			return ROLE_SALES;
		}
		return "";
	}

	private Map<Integer, String> loadBranchNameMap() {
		Map<Integer, String> m = new HashMap<>();
		for (Branch b : branchRepository.findAllByDeletedFalseOrderByIdAsc()) {
			m.put(b.getId(), b.getName());
		}
		return m;
	}
}
