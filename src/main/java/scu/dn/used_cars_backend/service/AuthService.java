package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.auth.LoginRequest;
import scu.dn.used_cars_backend.dto.auth.LoginResponse;
import scu.dn.used_cars_backend.dto.auth.RegisterRequest;
import scu.dn.used_cars_backend.dto.auth.RegisterResponse;
import scu.dn.used_cars_backend.dto.auth.UserProfileDto;
import scu.dn.used_cars_backend.entity.Role;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.repository.RoleRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.security.JwtService;

import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class AuthService {

	/** Phải trùng {@code Roles.name} trong seed — xem {@code docs/db_design/init_schema.sql}. */
	private static final String CUSTOMER_ROLE = "Customer";

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final BranchRepository branchRepository;

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findActiveByEmailWithRoles(request.getEmail().trim())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Sai email hoặc mật khẩu."));
		if (!"active".equalsIgnoreCase(user.getStatus())) {
			throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED, "Tài khoản bị khóa.");
		}
		if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Sai email hoặc mật khẩu.");
		}
		String roleName = resolvePrimaryRoleName(user);
		String token = jwtService.generateToken(user.getId(), user.getEmail(), roleName);
		UserProfileDto profile = UserProfileDto.builder()
				.id(user.getId())
				.name(user.getName())
				.email(user.getEmail())
				.phone(user.getPhone())
				.address(user.getAddress())
				.avatarUrl(user.getAvatarUrl())
				.dateOfBirth(user.getDateOfBirth())
				.gender(user.getGender())
				.role(roleName)
				.build();

		if (roleName.equals("BranchManager") || roleName.equals("SalesStaff")) {
			staffAssignmentRepository.findFirstByUserIdAndActiveTrueOrderByIdDesc(user.getId())
					.ifPresent(assignment -> profile.setBranchId(assignment.getBranchId()));
			if (profile.getBranchId() == null && "BranchManager".equals(roleName)) {
				branchRepository.findFirstByManager_IdAndDeletedFalse(user.getId())
						.map(Branch::getId)
						.ifPresent(profile::setBranchId);
			}
		}

		return new LoginResponse(profile, token);
	}

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		String email = request.getEmail().trim().toLowerCase();
		if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Email đã được sử dụng.");
		}
		Role customerRole = roleRepository.findByName(CUSTOMER_ROLE)
				.orElseThrow(() -> new IllegalStateException("Vai trò Customer chưa được seed trong database."));
		User user = new User();
		user.setName(request.getName().trim());
		user.setEmail(email);
		user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
		user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
		user.setAuthProvider("local");
		user.setStatus("active");
		user.setDeleted(false);
		UserRole link = new UserRole();
		link.setUser(user);
		link.setRole(customerRole);
		user.getUserRoles().add(link);
		userRepository.save(user);
		return new RegisterResponse("Tài khoản đã tạo. Vui lòng kiểm tra email xác thực.");
	}

	/**
	 * Vai trò ghi vào JWT: lấy role có id lớn nhất (Admin, BranchManager, SalesStaff, Customer theo thứ tự id seed).
	 * Tránh lỗi user có nhiều UserRoles mà min(id) luôn trả Customer → 403 trên API manager.
	 */
	private String resolvePrimaryRoleName(User user) {
		return user.getUserRoles().stream()
				.max(Comparator.comparingInt(ur -> ur.getRole().getId()))
				.map(ur -> ur.getRole().getName())
				.orElse(CUSTOMER_ROLE);
	}

	@Transactional
	public void changePassword(long userId, String currentPassword, String newPassword) {
		// B1: Lấy user từ DB
		User user = userRepository.findByIdAndDeletedFalse(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
		// B2: Kiểm tra mật khẩu hiện tại
		if (user.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD, "Mật khẩu hiện tại không đúng.");
		}
		// B3: Độ dài + khác mật cũ (khớp RegisterRequest: 8–100 ký tự; DTO @Valid đã chặn, giữ lớp phòng thủ)
		if (newPassword.length() < 8 || newPassword.length() > 100) {
			throw new BusinessException(ErrorCode.PASSWORD_TOO_SHORT, "Mật khẩu từ 8 đến 100 ký tự.");
		}
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mật khẩu mới phải khác mật khẩu hiện tại.");
		}
		// B4: Hash và lưu
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		userRepository.save(user);
	}
}
