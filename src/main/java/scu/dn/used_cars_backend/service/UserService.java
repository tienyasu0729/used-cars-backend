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
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.SalesOrderRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.interaction.repository.SavedVehicleRepository;
import scu.dn.used_cars_backend.sms.entity.OtpVerification;
import scu.dn.used_cars_backend.sms.repository.OtpVerificationRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Hồ sơ người dùng: cập nhật tên/SĐT, avatar, thống kê dashboard khách.
@Service
@RequiredArgsConstructor
public class UserService {

	private static final String CUSTOMER_ROLE = "Customer";
	private static final String PROFILE_OTP_REFERENCE = "profile";
	private static final long PROFILE_OTP_VERIFIED_MAX_AGE_MINUTES = 15;

	private final UserRepository userRepository;
	private final OtpVerificationRepository otpVerificationRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final BranchRepository branchRepository;
	private final SavedVehicleRepository savedVehicleRepository;
	private final BookingRepository bookingRepository;
	private final DepositRepository depositRepository;
	private final SalesOrderRepository salesOrderRepository;
	private final CloudinaryUploadService cloudinaryUploadService;

	@Transactional
	public void updateProfile(long userId, UpdateProfileRequest request) {
		User user = loadActiveUser(userId);
		if (userRepository.existsByPhoneIgnoreCaseAndDeletedFalseAndIdNot(request.phone(), userId)) {
			throw new BusinessException(ErrorCode.STAFF_PHONE_EXISTS, "Số điện thoại đã được sử dụng.");
		}
		if (isPhoneChanged(user.getPhone(), request.phone())) {
			assertProfilePhoneOtp(userId, request.otpVerificationId(), request.phone());
		}
		// Đã strip + chuẩn hoá SĐT + validate trong UpdateProfileRequest (Bean Validation)
		user.setName(request.name());
		user.setPhone(request.phone());
		user.setAddress(request.address());
		user.setDateOfBirth(request.dateOfBirth());
		user.setGender(request.gender());
		ProfileCompletionSupport.refreshProfileCompletionFlag(user);
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
				.role(roleName)
				.passwordChangeRequired(Boolean.TRUE.equals(user.getPasswordChangeRequired()))
				.hasPassword(hasPasswordSet(user))
				.googleLinked(isGoogleLinked(user))
				.profileCompletionRequired(Boolean.TRUE.equals(user.getProfileCompletionRequired()));
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
		// B1: Đếm xe đã lưu còn hiển thị công khai (loại Hidden/Sold/deleted)
		long saved = savedVehicleRepository.countVisibleSavedForUser(userId);
		// B2: Lịch Pending/Confirmed
		long upcoming = bookingRepository.countUpcomingByCustomerId(userId);
		long activeDeposits = depositRepository.countByCustomerIdAndStatusIn(userId, List.of("Pending", "Confirmed"));
		long totalOrders = salesOrderRepository.countByCustomerId(userId);
		return CustomerStatsResponse.builder()
				.savedVehicles(saved)
				.upcomingBookings(upcoming)
				.activeDeposits(activeDeposits)
				.totalOrders(totalOrders)
				.build();
	}

	private static boolean isPhoneChanged(String currentPhone, String newPhone) {
		String current = currentPhone == null ? "" : currentPhone.trim();
		String next = newPhone == null ? "" : newPhone.trim();
		return !current.equalsIgnoreCase(next);
	}

	private void assertProfilePhoneOtp(long userId, Long otpVerificationId, String newPhone) {
		if (otpVerificationId == null) {
			throw new BusinessException(ErrorCode.OTP_REFERENCE_INVALID,
					"Cần xác thực OTP trước khi đổi số điện thoại.");
		}
		OtpVerification otp = otpVerificationRepository.findById(otpVerificationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.OTP_REFERENCE_INVALID,
						"Mã xác thực OTP không hợp lệ."));
		if (!PROFILE_OTP_REFERENCE.equals(otp.getReferenceType())) {
			throw new BusinessException(ErrorCode.OTP_REFERENCE_INVALID, "Mã OTP không thuộc cập nhật hồ sơ.");
		}
		if (!OtpVerification.STATUS_VERIFIED.equals(otp.getStatus())) {
			throw new BusinessException(ErrorCode.OTP_REFERENCE_INVALID,
					"Mã OTP chưa được xác thực. Vui lòng xác thực lại.");
		}
		if (otp.getVerifiedAt() == null
				|| otp.getVerifiedAt().isBefore(Instant.now().minus(Duration.ofMinutes(PROFILE_OTP_VERIFIED_MAX_AGE_MINUTES)))) {
			throw new BusinessException(ErrorCode.OTP_EXPIRED, "Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới.");
		}
		if (otp.getReferenceId() != null && !Objects.equals(otp.getReferenceId(), userId)) {
			throw new BusinessException(ErrorCode.OTP_REFERENCE_INVALID, "Mã OTP không khớp với tài khoản.");
		}
		if (otp.getPhone() != null && newPhone != null && !newPhone.equals(otp.getPhone().trim())) {
			throw new BusinessException(ErrorCode.OTP_REFERENCE_INVALID,
					"Số điện thoại xác thực OTP không khớp với hồ sơ.");
		}
	}

	private User loadActiveUser(long userId) {
		User user = userRepository.findByIdAndDeletedFalse(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
		if (!"active".equalsIgnoreCase(user.getStatus())) {
			throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED, "Tài khoản bị khóa.");
		}
		return user;
	}

	/** User đã đặt mật khẩu (kể cả tài khoản Google đặt MK qua quên mật khẩu). */
	private static boolean hasPasswordSet(User user) {
		return user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
	}

	/** Tài khoản đã liên kết Google OAuth (có sub trong providerId). */
	private static boolean isGoogleLinked(User user) {
		return user.getProviderId() != null && !user.getProviderId().isBlank();
	}

}
