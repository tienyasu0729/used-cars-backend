package scu.dn.used_cars_backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.PasswordResetToken;
import scu.dn.used_cars_backend.entity.Role;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.PasswordResetTokenRepository;
import scu.dn.used_cars_backend.repository.RoleRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.security.JwtService;
import scu.dn.used_cars_backend.service.payment.PaymentGatewayConfigService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	/** Phải trùng {@code Roles.name} trong seed — xem {@code docs/db_design/init_schema.sql}. */
	private static final String CUSTOMER_ROLE = "Customer";

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final BranchRepository branchRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
	private final PaymentGatewayConfigService paymentGatewayConfigService;

	@Value("${app.mail.from:}")
	private String mailFromProp;

	@Value("${spring.mail.username:}")
	private String springMailUsername;

	@Value("${spring.mail.password:}")
	private String springMailPassword;

	@Value("${spring.mail.host:}")
	private String springMailHost;

	@Value("${app.google.client-id:}")
	private String googleClientId;

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
		UserProfileDto profile = buildLoginProfile(user, roleName);
		return new LoginResponse(profile, token);
	}

	private UserProfileDto buildLoginProfile(User user, String roleName) {
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
				.passwordChangeRequired(Boolean.TRUE.equals(user.getPasswordChangeRequired()))
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
		return profile;
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
		user.setPasswordChangeRequired(false);
		UserRole link = new UserRole();
		link.setUser(user);
		link.setRole(customerRole);
		user.getUserRoles().add(link);
		userRepository.save(user);
		return new RegisterResponse("Tài khoản đã tạo. Vui lòng kiểm tra email xác thực.");
	}

	// ===================== ĐĂNG NHẬP BẰNG GOOGLE =====================

	/**
	 * Đăng nhập / đăng ký bằng Google ID Token.
	 *
	 * Flow:
	 * B1: Verify Google ID Token bằng GoogleIdTokenVerifier (gọi Google server)
	 * B2: Lấy email, name, picture, sub từ payload
	 * B3: Tìm user theo email trong DB
	 * B4: Nếu chưa có → tạo user mới với authProvider="google", role Customer
	 * B5: Nếu đã có → link Google vào account (cập nhật providerId, avatar nếu chưa có)
	 * B6: Kiểm tra account bị khóa → throw ACCOUNT_SUSPENDED
	 * B7: Generate JWT và trả LoginResponse (cùng format với login thường)
	 */
	@Transactional
	public LoginResponse googleLogin(String idTokenString) {
		// B1: Kiểm tra Google Client ID đã cấu hình chưa
		if (googleClientId == null || googleClientId.isBlank()) {
			log.warn("app.google.client-id chưa cấu hình. Không thể xác thực Google.");
			throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED,
					"Đăng nhập Google chưa được cấu hình trên server.");
		}

		// B2: Verify Google ID Token
		GoogleIdToken.Payload payload = verifyGoogleToken(idTokenString);

		// B3: Lấy thông tin từ Google payload
		String email = payload.getEmail();
		String name = (String) payload.get("name");
		String picture = (String) payload.get("picture");
		String googleSub = payload.getSubject();

		if (email == null || email.isBlank()) {
			throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED,
					"Không lấy được email từ tài khoản Google.");
		}

		// B4: Tìm user theo email trong DB
		Optional<User> optUser = userRepository.findActiveByEmailWithRoles(email.trim().toLowerCase());

		User user;
		if (optUser.isPresent()) {
			// B5: User đã tồn tại → link Google vào account hiện tại
			user = optUser.get();

			// Kiểm tra tài khoản bị khóa
			if (!"active".equalsIgnoreCase(user.getStatus())) {
				throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED, "Tài khoản bị khóa.");
			}

			// Cập nhật providerId để link Google (giữ nguyên authProvider nếu là "local")
			if (user.getProviderId() == null || user.getProviderId().isBlank()) {
				user.setProviderId(googleSub);
			}
			// Cập nhật avatar nếu user chưa có
			if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) && picture != null) {
				user.setAvatarUrl(picture);
			}
			userRepository.save(user);
		} else {
			// B6: User chưa tồn tại → tạo mới với role Customer
			Role customerRole = roleRepository.findByName(CUSTOMER_ROLE)
					.orElseThrow(() -> new IllegalStateException("Vai trò Customer chưa được seed trong database."));

			user = new User();
			user.setName(name != null ? name.trim() : email.substring(0, email.indexOf('@')));
			user.setEmail(email.trim().toLowerCase());
			user.setPasswordHash(null);
			user.setAuthProvider("google");
			user.setProviderId(googleSub);
			user.setAvatarUrl(picture);
			user.setStatus("active");
			user.setDeleted(false);
			user.setPasswordChangeRequired(false);

			UserRole link = new UserRole();
			link.setUser(user);
			link.setRole(customerRole);
			user.getUserRoles().add(link);

			userRepository.save(user);
		}

		// B7: Generate JWT và trả response (cùng format với login thường)
		String roleName = resolvePrimaryRoleName(user);
		String token = jwtService.generateToken(user.getId(), user.getEmail(), roleName);
		UserProfileDto profile = buildLoginProfile(user, roleName);
		return new LoginResponse(profile, token);
	}

	// Gọi Google server để verify ID Token, trả về payload nếu hợp lệ
	private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
		try {
			GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
					new NetHttpTransport(), GsonFactory.getDefaultInstance())
					.setAudience(Collections.singletonList(googleClientId))
					.build();

			GoogleIdToken idToken = verifier.verify(idTokenString);
			if (idToken == null) {
				throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED,
						"Google ID Token không hợp lệ hoặc đã hết hạn.");
			}
			return idToken.getPayload();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.warn("Lỗi verify Google ID Token: {}", e.getMessage());
			throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED,
					"Xác thực Google thất bại. Vui lòng thử lại.");
		}
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
	public LoginResponse completeRequiredPasswordChange(long userId, String newPassword) {
		User user = userRepository.findByIdAndDeletedFalse(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
		if (!Boolean.TRUE.equals(user.getPasswordChangeRequired())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tài khoản không yêu cầu đặt mật khẩu mới.");
		}
		if (newPassword.length() < 8 || newPassword.length() > 100) {
			throw new BusinessException(ErrorCode.PASSWORD_TOO_SHORT, "Mật khẩu từ 8 đến 100 ký tự.");
		}
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mật khẩu mới phải khác mật khẩu tạm hiện tại.");
		}
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		user.setPasswordChangeRequired(false);
		userRepository.save(user);
		String roleName = resolvePrimaryRoleName(user);
		UserProfileDto profile = buildLoginProfile(user, roleName);
		profile.setPasswordChangeRequired(false);
		String token = jwtService.generateToken(user.getId(), user.getEmail(), roleName);
		return new LoginResponse(profile, token);
	}

	@Transactional
	public void changePassword(long userId, String currentPassword, String newPassword) {
		// B1: Lấy user từ DB
		User user = userRepository.findByIdAndDeletedFalse(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
		if (Boolean.TRUE.equals(user.getPasswordChangeRequired())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Bạn đang dùng mật khẩu tạm. Vui lòng hoàn tất màn hình đặt mật khẩu mới sau đăng nhập.");
		}
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
		user.setPasswordChangeRequired(false);
		userRepository.save(user);
	}

	// ===================== QUÊN MẬT KHẨU =====================

	/**
	 * Yêu cầu đặt lại mật khẩu qua email.
	 * Luôn trả về bình thường bất kể email có tồn tại hay không (tránh lộ thông tin).
	 */
	@Transactional
	public void requestPasswordReset(String email) {
		// B1: Tìm user theo email, nếu không thấy thì return luôn (không lộ thông tin)
		Optional<User> optUser = userRepository.findActiveByEmailWithRoles(email.trim().toLowerCase());
		if (optUser.isEmpty()) {
			return;
		}
		User user = optUser.get();

		// B2: Xóa token cũ của user nếu có
		passwordResetTokenRepository.deleteByUserId(user.getId());

		// B3: Sinh token ngẫu nhiên an toàn (64 ký tự hex = 32 byte)
		byte[] randomBytes = new byte[32];
		new SecureRandom().nextBytes(randomBytes);
		String rawToken = HexFormat.of().formatHex(randomBytes);

		// B4: Hash token bằng SHA-256 trước khi lưu DB
		String hashedToken = sha256Hex(rawToken);

		// B5: Lưu PasswordResetToken vào DB (hết hạn sau 15 phút)
		PasswordResetToken resetToken = new PasswordResetToken();
		resetToken.setUser(user);
		resetToken.setToken(hashedToken);
		resetToken.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
		resetToken.setUsed(false);
		passwordResetTokenRepository.save(resetToken);

		// B6: Gửi email chứa link đặt lại mật khẩu
		sendResetEmail(user, rawToken);
	}

	/**
	 * Đặt lại mật khẩu bằng token nhận từ email.
	 * Không trả JWT — user phải đăng nhập lại thủ công.
	 */
	@Transactional
	public void resetPassword(String token, String newPassword) {
		// B1: Hash token input bằng SHA-256 để tìm trong DB
		String hashedToken = sha256Hex(token);

		// B2: Tìm token trong DB
		PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(hashedToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN,
						"Token không hợp lệ hoặc đã hết hạn."));

		// B3: Kiểm tra token chưa dùng và chưa hết hạn
		if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(Instant.now())) {
			throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN,
					"Token không hợp lệ hoặc đã hết hạn.");
		}

		// B4: Validate độ dài mật khẩu mới (8–100 ký tự)
		if (newPassword.length() < 8 || newPassword.length() > 100) {
			throw new BusinessException(ErrorCode.PASSWORD_TOO_SHORT, "Mật khẩu từ 8 đến 100 ký tự.");
		}

		// B5: Lấy user và kiểm tra mật khẩu mới khác mật khẩu cũ
		User user = resetToken.getUser();
		if (user.getPasswordHash() != null && passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mật khẩu mới phải khác mật khẩu hiện tại.");
		}

		// B6: Cập nhật mật khẩu mới
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		user.setPasswordChangeRequired(false);
		userRepository.save(user);

		// B7: Đánh dấu token đã dùng
		resetToken.setUsed(true);
		passwordResetTokenRepository.save(resetToken);
	}

	// Gửi email chứa link đặt lại mật khẩu
	private void sendResetEmail(User user, String rawToken) {
		// Kiểm tra SMTP đã cấu hình chưa
		JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
		if (sender == null) {
			log.warn("SMTP chưa cấu hình, không gửi được email đặt lại mật khẩu cho user {}", user.getId());
			return;
		}

		String from = (mailFromProp != null && !mailFromProp.isBlank()) ? mailFromProp : springMailUsername;
		if (from == null || from.isBlank()) {
			log.warn("MAIL_FROM / spring.mail.username trống, không gửi được email đặt lại mật khẩu.");
			return;
		}
		if (springMailPassword == null || springMailPassword.isBlank()) {
			log.warn(
					"spring.mail.password đang trống — SMTP không xác thực được. Kiểm tra: (1) application-local.yml có App Password; (2) biến môi trường SPRING_MAIL_PASSWORD hoặc MAIL_PASSWORD không đè rỗng lên file cấu hình. userId={}",
					user.getId());
			return;
		}

		// Gmail App Password luôn có đúng 16 ký tự chữ thường — cảnh báo sớm nếu sai
		boolean isGmail = springMailHost != null && springMailHost.contains("gmail");
		if (isGmail && springMailPassword.length() != 16) {
			log.warn(
					"Gmail App Password phải có đúng 16 ký tự nhưng hiện tại có {} ký tự. "
							+ "Kiểm tra: (1) App Password trong application-local.yml có đầy đủ không (copy thiếu?); "
							+ "(2) biến môi trường MAIL_PASSWORD / SPRING_MAIL_PASSWORD có đang đè giá trị sai không. userId={}",
					springMailPassword.length(), user.getId());
		}

		try {
			String frontendBaseUrl = paymentGatewayConfigService.frontendBaseUrl();
			String resetLink = frontendBaseUrl + "/reset-password?token=" + rawToken;

			String subject = "Đặt lại mật khẩu — BanXeOTô Đà Nẵng";
			String body = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">"
					+ "<h2 style=\"color: #E8612A;\">Đặt lại mật khẩu</h2>"
					+ "<p>Xin chào <b>" + user.getName() + "</b>,</p>"
					+ "<p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.</p>"
					+ "<p>Nhấn vào link bên dưới để đặt mật khẩu mới (link có hiệu lực 15 phút):</p>"
					+ "<p><a href=\"" + resetLink + "\" style=\"display: inline-block; padding: 12px 24px; "
					+ "background-color: #E8612A; color: #ffffff; text-decoration: none; border-radius: 6px;\">"
					+ "Đặt lại mật khẩu</a></p>"
					+ "<p style=\"color: #888; font-size: 13px;\">Nếu bạn không yêu cầu đặt lại mật khẩu, "
					+ "vui lòng bỏ qua email này.</p>"
					+ "</div>";

			MimeMessage mm = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mm, false, "UTF-8");
			helper.setFrom(from);
			helper.setTo(user.getEmail());
			helper.setSubject(subject);
			helper.setText(body, true);
			sender.send(mm);
		} catch (Exception e) {
			logSmtpSendFailure(user.getId(), e);
		}
	}

	// Gmail 535-5.7.8 BadCredentials: sai tài khoản SMTP hoặc chưa dùng App Password — không sửa được bằng code
	private void logSmtpSendFailure(long userId, Exception e) {
		if (isSmtpAuthenticationFailure(e)) {
			int pwdLen = (springMailPassword != null) ? springMailPassword.length() : 0;
			log.warn(
					"SMTP từ chối xác thực (Gmail 535 BadCredentials). "
							+ "password.length={} (Gmail App Password phải đúng 16). "
							+ "Cần: đúng email + App Password 16 ký tự "
							+ "(bật 2FA → https://myaccount.google.com/apppasswords ), không dùng mật khẩu đăng nhập web. "
							+ "Kiểm tra: (1) App Password đúng 16 ký tự chưa; "
							+ "(2) SPRING_MAIL_PASSWORD/MAIL_PASSWORD env var không đè sai. userId={}",
					pwdLen, userId, e);
			return;
		}
		log.warn("Gửi email đặt lại mật khẩu thất bại cho user {}: {}", userId, e.getMessage(), e);
	}

	private static boolean isSmtpAuthenticationFailure(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof MailAuthenticationException || t instanceof AuthenticationFailedException) {
				return true;
			}
			String m = t.getMessage();
			if (m != null && (m.contains("535") || m.contains("BadCredentials") || "Authentication failed".equals(m))) {
				return true;
			}
		}
		return false;
	}

	// Hash chuỗi bằng SHA-256, trả về hex string
	private String sha256Hex(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			throw new RuntimeException("Lỗi SHA-256", e);
		}
	}
}
