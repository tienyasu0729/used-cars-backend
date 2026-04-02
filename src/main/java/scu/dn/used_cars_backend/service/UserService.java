package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.CustomerStatsResponse;
import scu.dn.used_cars_backend.dto.UpdateProfileRequest;
import scu.dn.used_cars_backend.dto.auth.UserProfileDto;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.interaction.repository.SavedVehicleRepository;

import java.util.Comparator;
import java.util.Optional;

// Hồ sơ người dùng: cập nhật tên/SĐT, avatar, thống kê dashboard khách.
@Service
@RequiredArgsConstructor
public class UserService {

	private static final String CUSTOMER_ROLE = "Customer";

	private final UserRepository userRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final BranchRepository branchRepository;
	private final SavedVehicleRepository savedVehicleRepository;
	private final BookingRepository bookingRepository;
	private final CloudinaryUploadService cloudinaryUploadService;

	@Transactional
	public void updateProfile(long userId, UpdateProfileRequest request) {
		User user = loadActiveUser(userId);
		// Đã strip + chuẩn hoá SĐT + validate trong UpdateProfileRequest (Bean Validation)
		user.setName(request.name());
		user.setPhone(request.phone());
		user.setAddress(request.address());
		user.setDateOfBirth(request.dateOfBirth());
		user.setGender(request.gender());
		userRepository.save(user);
	}

	@Transactional(readOnly = true)
	public UserProfileDto getMeProfile(long userId) {
		User user = userRepository.findActiveByIdWithRoles(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
		if (!"active".equalsIgnoreCase(user.getStatus())) {
			throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED, "Tài khoản bị khóa.");
		}
		String roleName = resolvePrimaryRoleName(user);
		UserProfileDto.UserProfileDtoBuilder b = UserProfileDto.builder()
				.id(user.getId())
				.name(user.getName())
				.email(user.getEmail())
				.phone(user.getPhone())
				.address(user.getAddress())
				.avatarUrl(user.getAvatarUrl())
				.dateOfBirth(user.getDateOfBirth())
				.gender(user.getGender())
				.role(roleName);
		resolveProfileBranchId(user.getId(), roleName).ifPresent(b::branchId);
		return b.build();
	}

	/** Chi nhánh hiển thị trên profile: StaffAssignments active, hoặc Branches.manager_id (BranchManager). */
	private Optional<Integer> resolveProfileBranchId(long userId, String roleName) {
		if (!"BranchManager".equals(roleName) && !"SalesStaff".equals(roleName)) {
			return Optional.empty();
		}
		Optional<Integer> fromSa = staffAssignmentRepository.findFirstByUserIdAndActiveTrueOrderByIdDesc(userId)
				.map(sa -> sa.getBranchId());
		if (fromSa.isPresent()) {
			return fromSa;
		}
		if ("BranchManager".equals(roleName)) {
			return branchRepository.findFirstByManager_IdAndDeletedFalse(userId).map(Branch::getId);
		}
		return Optional.empty();
	}

	private static String resolvePrimaryRoleName(User user) {
		return user.getUserRoles().stream()
				.min(Comparator.comparingInt(ur -> ur.getRole().getId()))
				.map(ur -> ur.getRole().getName())
				.orElse(CUSTOMER_ROLE);
	}

	@Transactional
	public String saveAvatarFromCloudinaryUrl(long userId, String secureUrl) {
		// B1: Xác thực URL do client upload trực tiếp (đúng cloud / folder / user)
		cloudinaryUploadService.assertSecureUrlMatchesSignedContext(secureUrl, MediaUploadContext.AVATAR, userId);
		// B2: Cập nhật DB
		User user = loadActiveUser(userId);
		user.setAvatarUrl(secureUrl.trim());
		userRepository.save(user);
		// B3: Trả URL client
		return user.getAvatarUrl();
	}

	@Transactional(readOnly = true)
	public CustomerStatsResponse getCustomerStats(long userId) {
		// B1: Đếm xe đã lưu
		long saved = savedVehicleRepository.countByIdUserId(userId);
		// B2: Lịch Pending/Confirmed
		long upcoming = bookingRepository.countUpcomingByCustomerId(userId);
		// TODO: Will be implemented in later sprint (Tier 4 — activeDeposits / totalOrders từ DB thật)
		return CustomerStatsResponse.builder()
				.savedVehicles(saved)
				.upcomingBookings(upcoming)
				.activeDeposits(0)
				.totalOrders(0)
				.build();
	}

	private User loadActiveUser(long userId) {
		User user = userRepository.findByIdAndDeletedFalse(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
		if (!"active".equalsIgnoreCase(user.getStatus())) {
			throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED, "Tài khoản bị khóa.");
		}
		return user;
	}

}
