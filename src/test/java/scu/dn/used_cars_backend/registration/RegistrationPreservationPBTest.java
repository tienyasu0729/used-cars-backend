package scu.dn.used_cars_backend.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.Role;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.RoleRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.UserRoleRepository;
import scu.dn.used_cars_backend.security.JwtService;
import scu.dn.used_cars_backend.sms.repository.OtpVerificationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 *
 * Property 2: Preservation - Non-Duplicate Registration Flow Unchanged
 *
 * Tests verify baseline behavior on UNFIXED code:
 * - Valid registration inputs (email/phone mới) → HTTP 200 + OTP response
 * - Invalid inputs (field rỗng/format sai) → Bean Validation VALIDATION_FAILED + errors[]
 * - Staff endpoints vẫn trả STAFF_PHONE_EXISTS/STAFF_EMAIL_EXISTS với HTTP 409
 */
@JqwikSpringSupport
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationPreservationPBTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRoleRepository userRoleRepository;

	@Autowired
	private OtpVerificationRepository otpVerificationRepository;

	@Autowired
	private ObjectProvider<StringRedisTemplate> redisProvider;

	private void ensureExistingUser(String email, String phone) {
		if (!userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
			User u = new User();
			u.setName("Existing User");
			u.setEmail(email.toLowerCase());
			u.setPhone(phone);
			u.setPasswordHash(passwordEncoder.encode("password123"));
			u.setDeleted(false);
			userRepository.save(u);
		}
	}

	private void ensureSalesStaffRole() {
		if (roleRepository.findByName("SalesStaff").isEmpty()) {
			Role r = new Role();
			r.setName("SalesStaff");
			r.setDescription("Nhân viên bán hàng");
			r.setSystemRole(true);
			roleRepository.save(r);
		}
	}

	private void ensureBranchExists() {
		if (branchRepository.findByIdAndDeletedFalse(1).isEmpty()) {
			Branch b = new Branch();
			b.setName("Chi nhánh Test");
			b.setAddress("123 Test Street");
			b.setPhone("0901000000");
			b.setStatus("active");
			b.setDeleted(false);
			branchRepository.save(b);
		}
	}

	@Property(tries = 3, shrinking = ShrinkingMode.OFF)
	@Label("Preservation: valid registration inputs (new email/phone) return HTTP 200 + OTP response")
	void validRegistrationReturnsOtpSuccess(
			@ForAll("validNewRegistrationInputs") RegistrationInput input) throws Exception {

		otpVerificationRepository.deleteAll();
		StringRedisTemplate redis = redisProvider.getIfAvailable();
		if (redis != null) {
			redis.delete(redis.keys("otp:rate:*"));
			redis.delete(redis.keys("otp:hourly:*"));
		}

		String requestBody = """
				{"email":"%s","phone":"%s"}
				""".formatted(input.email(), input.phone());

		MvcResult result = mockMvc.perform(
				post("/api/v1/auth/register/request-otp")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andReturn();

		int status = result.getResponse().getStatus();
		String responseBody = result.getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(responseBody);

		assertThat(status)
				.as("HTTP status should be 200 for valid new email/phone, got: " + responseBody)
				.isEqualTo(200);
		assertThat(root.has("success") && root.get("success").asBoolean())
				.as("response.success should be true")
				.isTrue();
		assertThat(root.has("data"))
				.as("response should have data field")
				.isTrue();
		JsonNode data = root.get("data");
		assertThat(data.has("otpId"))
				.as("OTP response should contain otpId")
				.isTrue();
		assertThat(data.has("expiresAt"))
				.as("OTP response should contain expiresAt")
				.isTrue();
	}

	@Property(tries = 10)
	@Label("Preservation: invalid inputs (empty/bad format) return VALIDATION_FAILED + errors[]")
	void invalidInputsReturnBeanValidationErrors(
			@ForAll("invalidRegistrationInputs") RegistrationInput input) throws Exception {

		String requestBody = """
				{"email":"%s","phone":"%s"}
				""".formatted(
				input.email() != null ? input.email() : "",
				input.phone() != null ? input.phone() : "");

		MvcResult result = mockMvc.perform(
				post("/api/v1/auth/register/request-otp")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andReturn();

		int status = result.getResponse().getStatus();
		String responseBody = result.getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(responseBody);

		assertThat(status)
				.as("HTTP status should be 400 for invalid inputs")
				.isEqualTo(400);
		assertThat(root.get("errorCode").asText())
				.as("errorCode should be VALIDATION_FAILED")
				.isEqualTo("VALIDATION_FAILED");
		assertThat(root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty())
				.as("errors[] should be present and non-empty from Bean Validation")
				.isTrue();

		for (JsonNode err : root.get("errors")) {
			assertThat(err.has("field"))
					.as("Each error should have a 'field' property")
					.isTrue();
			assertThat(err.has("message"))
					.as("Each error should have a 'message' property")
					.isTrue();
		}
	}

	private String getAdminToken() {
		Role adminRole = roleRepository.findByName("Admin").orElseGet(() -> {
			Role r = new Role();
			r.setName("Admin");
			r.setDescription("Admin");
			r.setSystemRole(true);
			return roleRepository.save(r);
		});
		String adminEmail = "pres_admin@test.com";
		User admin = userRepository.findActiveByEmailWithRoles(adminEmail).orElseGet(() -> {
			User u = new User();
			u.setName("Admin Test");
			u.setEmail(adminEmail);
			u.setPhone("0800000000");
			u.setPasswordHash(passwordEncoder.encode("admin123"));
			u.setAuthProvider("local");
			u.setStatus("active");
			u.setDeleted(false);
			u.setPasswordChangeRequired(false);
			User saved = userRepository.save(u);
			UserRole link = new UserRole();
			link.setUser(saved);
			link.setRole(adminRole);
			userRoleRepository.save(link);
			return userRepository.findActiveByEmailWithRoles(adminEmail).orElseThrow();
		});
		return jwtService.generateToken(admin.getId(), admin.getEmail(), "Admin");
	}

	@Property(tries = 5)
	@Label("Preservation: staff endpoint with duplicate phone returns STAFF_PHONE_EXISTS + HTTP 409")
	void staffDuplicatePhoneReturnsStaffPhoneExists(
			@ForAll("staffDuplicatePhoneInputs") StaffInput input) throws Exception {

		ensureExistingUser(input.existingEmail(), input.existingPhone());
		ensureSalesStaffRole();
		ensureBranchExists();
		String adminToken = getAdminToken();

		String requestBody = """
				{"name":"%s","email":"%s","phone":"%s","password":"%s","branchId":%d}
				""".formatted(input.name(), input.newEmail(), input.existingPhone(),
				input.password(), input.branchId());

		MvcResult result = mockMvc.perform(
				post("/api/v1/manager/staff")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody)
						.header("Authorization", "Bearer " + adminToken))
				.andReturn();

		int status = result.getResponse().getStatus();
		String responseBody = result.getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(responseBody);

		assertThat(status)
				.as("HTTP status should be 409 for staff duplicate phone")
				.isEqualTo(409);
		assertThat(root.get("errorCode").asText())
				.as("errorCode should be STAFF_PHONE_EXISTS")
				.isEqualTo("STAFF_PHONE_EXISTS");
	}

	@Property(tries = 5)
	@Label("Preservation: staff endpoint with duplicate email returns STAFF_EMAIL_EXISTS + HTTP 409")
	void staffDuplicateEmailReturnsStaffEmailExists(
			@ForAll("staffDuplicateEmailInputs") StaffInput input) throws Exception {

		ensureExistingUser(input.existingEmail(), input.existingPhone());
		ensureSalesStaffRole();
		ensureBranchExists();
		String adminToken = getAdminToken();

		String requestBody = """
				{"name":"%s","email":"%s","phone":"%s","password":"%s","branchId":%d}
				""".formatted(input.name(), input.existingEmail(), input.newPhone(),
				input.password(), input.branchId());

		MvcResult result = mockMvc.perform(
				post("/api/v1/manager/staff")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody)
						.header("Authorization", "Bearer " + adminToken))
				.andReturn();

		int status = result.getResponse().getStatus();
		String responseBody = result.getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(responseBody);

		assertThat(status)
				.as("HTTP status should be 409 for staff duplicate email")
				.isEqualTo(409);
		assertThat(root.get("errorCode").asText())
				.as("errorCode should be STAFF_EMAIL_EXISTS")
				.isEqualTo("STAFF_EMAIL_EXISTS");
	}

	@Provide
	Arbitrary<RegistrationInput> validNewRegistrationInputs() {
		Arbitrary<Long> suffixes = Arbitraries.longs().between(100000000L, 999999999L);
		return suffixes.map(s -> new RegistrationInput(
				"pres_" + s + "@test.com",
				"0" + String.valueOf(s)));
	}

	@Provide
	Arbitrary<RegistrationInput> invalidRegistrationInputs() {
		return Arbitraries.of(
				new RegistrationInput("", "0901234567"),
				new RegistrationInput("not-an-email", "0901234567"),
				new RegistrationInput("valid@test.com", ""),
				new RegistrationInput("valid@test.com", "123"),
				new RegistrationInput("valid@test.com", "abcdefghij"),
				new RegistrationInput("", ""),
				new RegistrationInput("bad", "bad")
		);
	}

	@Provide
	Arbitrary<StaffInput> staffDuplicatePhoneInputs() {
		Arbitrary<Integer> suffixes = Arbitraries.integers().between(100, 999);
		return suffixes.map(s -> new StaffInput(
				"Staff Pres " + s,
				"staff_pres_phone_" + s + "@test.com",
				"08" + String.format("%08d", s),
				"staff_pres_new_" + s + "@test.com",
				null,
				"Password123!",
				1));
	}

	@Provide
	Arbitrary<StaffInput> staffDuplicateEmailInputs() {
		Arbitrary<Integer> suffixes = Arbitraries.integers().between(100, 999);
		return suffixes.map(s -> new StaffInput(
				"Staff Pres " + s,
				"staff_pres_email_" + s + "@test.com",
				"07" + String.format("%08d", s),
				null,
				"06" + String.format("%08d", s),
				"Password123!",
				1));
	}

	record RegistrationInput(String email, String phone) {}

	record StaffInput(String name, String existingEmail, String existingPhone,
					  String newEmail, String newPhone, String password, int branchId) {}
}
