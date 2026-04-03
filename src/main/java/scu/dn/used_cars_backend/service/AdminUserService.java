package scu.dn.used_cars_backend.service;

// Service quản lý user toàn hệ thống cho Admin — CRUD, khóa tài khoản, reset mật khẩu.
// B1: validate + map trạng thái API ↔ DB (locked ↔ suspended). B2: UserRoles + StaffAssignment + Branches.manager.

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.admin.AdminResetPasswordResponse;
import scu.dn.used_cars_backend.dto.admin.AdminUserListItemDto;
import scu.dn.used_cars_backend.dto.admin.AdminUserStatusPatchRequest;
import scu.dn.used_cars_backend.dto.admin.CreateAdminUserRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminUserRequest;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.Role;
import scu.dn.used_cars_backend.entity.StaffAssignment;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.RoleRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.UserRoleRepository;
import scu.dn.used_cars_backend.repository.spec.UserAdminSpecs;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

	private static final String ROLE_CUSTOMER = "Customer";
	private static final String ROLE_SALES = "SalesStaff";
	private static final String ROLE_MANAGER = "BranchManager";
	private static final String ROLE_ADMIN = "Admin";
	private static final Set<String> ASSIGNABLE_ROLES = Set.of(ROLE_CUSTOMER, ROLE_SALES, ROLE_MANAGER, ROLE_ADMIN);

	private static final String DB_SUSPENDED = "suspended";
	private static final String API_LOCKED = "locked";

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleRepository roleRepository;
	private final BranchRepository branchRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final PasswordEncoder passwordEncoder;

	private static final String ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	@Transactional(readOnly = true)
	public Page<AdminUserListItemDto> listUsers(String roleFilter, String statusFilter, String search, int page,
			int size) {
		// B1: ghép điều kiện Specification
		Specification<User> spec = UserAdminSpecs.notDeleted();
		if (roleFilter != null && !roleFilter.isBlank()) {
			spec = spec.and(UserAdminSpecs.hasRoleName(roleFilter.trim()));
		}
		if (statusFilter != null && !statusFilter.isBlank()) {
			String db = statusApiToDb(statusFilter.trim());
			spec = spec.and(UserAdminSpecs.statusEqualsDb(db));
		}
		if (search != null && !search.isBlank()) {
			spec = spec.and(UserAdminSpecs.searchLike(search));
		}
		// B2: phân trang + map DTO
		PageRequest pr = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "id"));
		Page<User> pg = userRepository.findAll(spec, pr);
		Map<Integer, String> branchNames = loadBranchNameMap();
		return pg.map(u -> toListItem(u, branchNames));
	}

	@Transactional
	public long createUser(CreateAdminUserRequest req) {
		String roleName = normalizeRoleName(req.getRole());
		if (!ASSIGNABLE_ROLES.contains(roleName)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Vai trò không hợp lệ.");
		}
		if (ROLE_SALES.equals(roleName) || ROLE_MANAGER.equals(roleName)) {
			if (req.getBranchId() == null) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "branchId bắt buộc với SalesStaff/BranchManager.");
			}
			branchRepository.findByIdAndDeletedFalse(req.getBranchId())
					.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		}
		String email = req.getEmail().trim().toLowerCase();
		if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
			throw new BusinessException(ErrorCode.USER_EMAIL_EXISTS, "Email đã được sử dụng.");
		}
		String phone = normalizePhone(req.getPhone());
		if (phone != null && userRepository.existsByPhoneIgnoreCaseAndDeletedFalse(phone)) {
			throw new BusinessException(ErrorCode.STAFF_PHONE_EXISTS, "Số điện thoại đã được sử dụng.");
		}
		Role role = roleRepository.findByName(roleName)
				.orElseThrow(() -> new IllegalStateException("Role seed thiếu: " + roleName));
		User user = new User();
		user.setName(req.getName().trim());
		user.setEmail(email);
		user.setPhone(phone);
		user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
		user.setAuthProvider("local");
		user.setStatus("active");
		user.setDeleted(false);
		user.setPasswordChangeRequired(false);
		UserRole link = new UserRole();
		link.setUser(user);
		link.setRole(role);
		user.getUserRoles().add(link);
		User saved = userRepository.save(user);
		syncStaffAndBranchManager(saved, roleName, req.getBranchId());
		return saved.getId();
	}

	@Transactional
	public void updateUser(long userId, UpdateAdminUserRequest req) {
		User user = loadUserForAdmin(userId);
		String roleName = normalizeRoleName(req.getRole());
		if (!ASSIGNABLE_ROLES.contains(roleName)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Vai trò không hợp lệ.");
		}
		if (ROLE_SALES.equals(roleName) || ROLE_MANAGER.equals(roleName)) {
			if (req.getBranchId() == null) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "branchId bắt buộc với SalesStaff/BranchManager.");
			}
			branchRepository.findByIdAndDeletedFalse(req.getBranchId())
					.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		}
		String phone = normalizePhone(req.getPhone());
		if (phone != null && userRepository.existsByPhoneIgnoreCaseAndDeletedFalseAndIdNot(phone, userId)) {
			throw new BusinessException(ErrorCode.STAFF_PHONE_EXISTS, "Số điện thoại đã được sử dụng.");
		}
		String dbStatus = statusApiToDb(req.getStatus());
		assertAllowedDbStatus(dbStatus);
		user.setName(req.getName().trim());
		user.setPhone(phone);
		user.setStatus(dbStatus);
		replaceUserRole(user, roleName);
		userRepository.save(user);
		syncStaffAndBranchManager(user, roleName, req.getBranchId());
	}

	@Transactional
	public void patchStatus(long userId, AdminUserStatusPatchRequest req) {
		User user = loadUserForAdmin(userId);
		String dbStatus = statusApiToDb(req.getStatus());
		assertAllowedDbStatus(dbStatus);
		user.setStatus(dbStatus);
		userRepository.save(user);
	}

	@Transactional
	public void softDeleteUser(long userId) {
		User user = loadUserForAdmin(userId);
		user.setDeleted(true);
		userRepository.save(user);
		clearBranchManagerLinks(userId);
		LocalDate today = LocalDate.now();
		for (StaffAssignment sa : staffAssignmentRepository.findByUserIdAndActiveTrue(userId)) {
			sa.setActive(false);
			sa.setEndDate(today);
			staffAssignmentRepository.save(sa);
		}
	}

	@Transactional
	public AdminResetPasswordResponse resetPassword(long userId) {
		User user = loadUserForAdmin(userId);
		String temp = randomAlphanumeric(8);
		user.setPasswordHash(passwordEncoder.encode(temp));
		user.setPasswordChangeRequired(true);
		userRepository.save(user);
		return AdminResetPasswordResponse.builder().success(true).temporaryPassword(temp).build();
	}

	private User loadUserForAdmin(long userId) {
		return userRepository.findByIdAndDeletedFalse(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
	}

	private void replaceUserRole(User user, String roleName) {
		Role role = roleRepository.findByName(roleName)
				.orElseThrow(() -> new IllegalStateException("Role seed thiếu: " + roleName));
		userRoleRepository.deleteAllByUser_Id(user.getId());
		user.getUserRoles().clear();
		UserRole link = new UserRole();
		link.setUser(user);
		link.setRole(role);
		user.getUserRoles().add(link);
	}

	private void syncStaffAndBranchManager(User user, String roleName, Integer branchId) {
		long uid = user.getId();
		// B1: kết thúc phân công đang active
		LocalDate today = LocalDate.now();
		for (StaffAssignment sa : staffAssignmentRepository.findByUserIdAndActiveTrue(uid)) {
			sa.setActive(false);
			sa.setEndDate(today);
			staffAssignmentRepository.save(sa);
		}
		clearBranchManagerLinks(uid);
		// B2: gán lại theo vai trò
		if (ROLE_SALES.equals(roleName) || ROLE_MANAGER.equals(roleName)) {
			StaffAssignment sa = new StaffAssignment();
			sa.setUserId(uid);
			sa.setBranchId(branchId);
			sa.setStartDate(LocalDate.now());
			sa.setActive(true);
			staffAssignmentRepository.save(sa);
			if (ROLE_MANAGER.equals(roleName)) {
				Branch b = branchRepository.findByIdAndDeletedFalse(branchId)
						.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
				b.setManager(user);
				branchRepository.save(b);
			}
		}
	}

	private void clearBranchManagerLinks(long userId) {
		for (Branch b : branchRepository.findAllByManager_IdAndDeletedFalse(userId)) {
			b.setManager(null);
			branchRepository.save(b);
		}
	}

	private AdminUserListItemDto toListItem(User u, Map<Integer, String> branchNames) {
		Integer branchId = null;
		String branchName = null;
		Optional<StaffAssignment> sa = staffAssignmentRepository.findFirstByUserIdAndActiveTrueOrderByIdDesc(u.getId());
		if (sa.isPresent()) {
			branchId = sa.get().getBranchId();
			branchName = branchNames.get(branchId);
		} else {
			Optional<Branch> mb = branchRepository.findFirstByManager_IdAndDeletedFalse(u.getId());
			if (mb.isPresent()) {
				branchId = mb.get().getId();
				branchName = mb.get().getName();
			}
		}
		return AdminUserListItemDto.builder()
				.id(u.getId())
				.name(u.getName())
				.email(u.getEmail())
				.phone(u.getPhone())
				.role(resolveDisplayRoleName(u))
				.branchId(branchId)
				.branchName(branchName != null ? branchName : "")
				.status(statusDbToApi(u.getStatus()))
				.avatarUrl(u.getAvatarUrl())
				.createdAt(u.getCreatedAt())
				.build();
	}

	private static String resolveDisplayRoleName(User u) {
		Set<String> names = u.getUserRoles().stream().map(ur -> ur.getRole().getName()).collect(Collectors.toSet());
		if (names.contains(ROLE_ADMIN)) {
			return ROLE_ADMIN;
		}
		if (names.contains(ROLE_MANAGER)) {
			return ROLE_MANAGER;
		}
		if (names.contains(ROLE_SALES)) {
			return ROLE_SALES;
		}
		return ROLE_CUSTOMER;
	}

	private Map<Integer, String> loadBranchNameMap() {
		Map<Integer, String> m = new HashMap<>();
		for (Branch b : branchRepository.findAllByDeletedFalseOrderByIdAsc()) {
			m.put(b.getId(), b.getName());
		}
		return m;
	}

	private static String normalizeRoleName(String raw) {
		if (raw == null) {
			return "";
		}
		String t = raw.trim();
		// Cho phép alias sales / manager ngắn gọn (optional) — chuẩn hoá về seed
		if ("sales".equalsIgnoreCase(t) || "salesstaff".equalsIgnoreCase(t)) {
			return ROLE_SALES;
		}
		if ("manager".equalsIgnoreCase(t) || "branchmanager".equalsIgnoreCase(t)) {
			return ROLE_MANAGER;
		}
		return t;
	}

	private static String normalizePhone(String phone) {
		if (phone == null || phone.isBlank()) {
			return null;
		}
		return phone.trim();
	}

	/** API → DB: locked → suspended */
	private static String statusApiToDb(String api) {
		if (API_LOCKED.equalsIgnoreCase(api)) {
			return DB_SUSPENDED;
		}
		return api.trim().toLowerCase();
	}

	/** DB → API: suspended → locked */
	private static String statusDbToApi(String db) {
		if (db != null && DB_SUSPENDED.equalsIgnoreCase(db)) {
			return API_LOCKED;
		}
		return db == null ? "" : db.toLowerCase();
	}

	private static void assertAllowedDbStatus(String db) {
		if (!Set.of("active", "inactive", DB_SUSPENDED).contains(db.toLowerCase())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Trạng thái tài khoản không hợp lệ.");
		}
	}

	private static String randomAlphanumeric(int len) {
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
		}
		return sb.toString();
	}
}
