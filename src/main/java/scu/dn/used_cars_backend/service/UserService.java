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
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.interaction.repository.SavedVehicleRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

// Hồ sơ người dùng: cập nhật tên/SĐT, avatar, thống kê dashboard khách.
@Service
@RequiredArgsConstructor
public class UserService {

	private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024;
	private static final String CUSTOMER_ROLE = "Customer";

	private final UserRepository userRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final SavedVehicleRepository savedVehicleRepository;
	private final BookingRepository bookingRepository;

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
		if ("BranchManager".equals(roleName) || "SalesStaff".equals(roleName)) {
			staffAssignmentRepository.findFirstByUserIdAndActiveTrueOrderByIdDesc(user.getId())
					.ifPresent(a -> b.branchId(a.getBranchId()));
		}
		return b.build();
	}

	private static String resolvePrimaryRoleName(User user) {
		return user.getUserRoles().stream()
				.min(Comparator.comparingInt(ur -> ur.getRole().getId()))
				.map(ur -> ur.getRole().getName())
				.orElse(CUSTOMER_ROLE);
	}

	@Transactional
	public String saveAvatar(long userId, byte[] fileBytes, String contentType) {
		// B1: Kiểu file + kích thước
		String ext = resolveImageExtension(contentType);
		if (fileBytes.length > MAX_AVATAR_BYTES) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ảnh tối đa 2MB.");
		}
		// B2: Ghi đĩa
		Path dir = Path.of("uploads", "avatars");
		try {
			Files.createDirectories(dir);
			Path target = Path.of("uploads", "avatars", userId + "." + ext);
			Files.write(target, fileBytes);
		}
		catch (java.io.IOException e) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không lưu được file avatar.");
		}
		String relative = "/uploads/avatars/" + userId + "." + ext;
		// B3: Cập nhật DB
		User user = loadActiveUser(userId);
		user.setAvatarUrl(relative);
		userRepository.save(user);
		// B4: Trả URL client
		return relative;
	}

	@Transactional(readOnly = true)
	public CustomerStatsResponse getCustomerStats(long userId) {
		// B1: Đếm xe đã lưu
		long saved = savedVehicleRepository.countByIdUserId(userId);
		// B2: Lịch Pending/Confirmed
		long upcoming = bookingRepository.countUpcomingByCustomerId(userId);
		// B3–B4: Tier 4 tạm cố định 0
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

	private static String resolveImageExtension(String contentType) {
		if (contentType == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thiếu loại file ảnh.");
		}
		String ct = contentType.toLowerCase(Locale.ROOT);
		if (ct.contains("jpeg") || ct.contains("jpg")) {
			return "jpg";
		}
		if (ct.contains("png")) {
			return "png";
		}
		throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chỉ chấp nhận JPG hoặc PNG.");
	}
}
