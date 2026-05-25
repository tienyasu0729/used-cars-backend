package scu.dn.used_cars_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.entity.PasswordResetToken;
import scu.dn.used_cars_backend.entity.Role;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.repository.PasswordResetTokenRepository;
import scu.dn.used_cars_backend.repository.RoleRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.UserRoleRepository;
import scu.dn.used_cars_backend.security.JwtService;
import scu.dn.used_cars_backend.service.ProfileCompletionSupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserRoleRepository userRoleRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private JwtService jwtService;

	@BeforeEach
	void seedCustomerRole() {
		if (roleRepository.findByName("Customer").isEmpty()) {
			Role r = new Role();
			r.setName("Customer");
			r.setDescription("Khách hàng");
			r.setSystemRole(true);
			roleRepository.save(r);
		}
	}

	@Test
	void registerThenLogin() throws Exception {
		String email = "integration@test.local";
		String body = """
				{"name":"Test User","email":"%s","phone":"0900000000","password":"password123"}
				""".formatted(email);
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.message").exists());

		MvcResult loginResult = mockMvc
				.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.token").exists())
				.andExpect(jsonPath("$.data.user.role").value("Customer"))
				.andReturn();

		JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
		String token = root.get("data").get("token").asText();
		org.assertj.core.api.Assertions.assertThat(token).isNotBlank();

		mockMvc.perform(get("/api/v1/protected-placeholder")).andExpect(status().isUnauthorized());
	}

	@Test
	void loginFailsForBadPassword() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody@test.local\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
	}

	@Test
	void registerDuplicateEmail() throws Exception {
		String email = "dup@test.local";
		String payload = "{\"name\":\"A\",\"email\":\"" + email + "\",\"password\":\"password123\"}";
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	/**
	 * Google-only (passwordHash null) → forgot-password → reset → login email+password.
	 * authProvider và providerId giữ nguyên sau reset.
	 */
	@Test
	@Transactional
	void googleOnlyUser_canSetPasswordViaForgotResetThenLogin() throws Exception {
		String email = "google-dual@test.local";
		String providerId = "fake-google-sub-123";

		User user = new User();
		user.setName("Google Only");
		user.setEmail(email);
		user.setAuthProvider("google");
		user.setProviderId(providerId);
		user.setPasswordHash(null);
		user.setStatus("active");
		user.setDeleted(false);
		user.setPasswordChangeRequired(false);
		user = userRepository.save(user);

		Role customerRole = roleRepository.findByName("Customer").orElseThrow();
		UserRole link = new UserRole();
		link.setUser(user);
		link.setRole(customerRole);
		userRoleRepository.save(link);

		mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		// Token raw không đọc được từ DB (chỉ lưu hash) — tạo token test với giá trị đã biết
		String rawToken = "a".repeat(64);
		passwordResetTokenRepository.deleteByUserId(user.getId());
		PasswordResetToken resetToken = new PasswordResetToken();
		resetToken.setUser(user);
		resetToken.setToken(sha256Hex(rawToken));
		resetToken.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
		resetToken.setUsed(false);
		passwordResetTokenRepository.save(resetToken);

		mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"newpass123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"newpass123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.hasPassword").value(true))
				.andExpect(jsonPath("$.data.user.googleLinked").value(true));

		User refreshed = userRepository.findActiveByEmailWithRoles(email).orElseThrow();
		assertThat(refreshed.getProviderId()).isEqualTo(providerId);
		assertThat(refreshed.getAuthProvider()).isEqualTo("google");
		assertThat(refreshed.getPasswordHash()).isNotBlank();
	}

	@Test
	@Transactional
	void googleFirstCustomer_profileCompletionRequiredUntilPhoneSaved() throws Exception {
		String email = "google-profile@test.local";
		User user = new User();
		user.setName("Google New");
		user.setEmail(email);
		user.setAuthProvider("google");
		user.setProviderId("google-sub-profile");
		user.setPasswordHash(null);
		user.setStatus("active");
		user.setDeleted(false);
		user.setPasswordChangeRequired(false);
		user.setProfileCompletionRequired(true);
		User savedUser = userRepository.save(user);

		Role customerRole = roleRepository.findByName("Customer").orElseThrow();
		UserRole link = new UserRole();
		link.setUser(savedUser);
		link.setRole(customerRole);
		userRoleRepository.save(link);

		String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail(), "Customer");

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileCompletionRequired").value(true));

		String updateBody = """
				{"name":"Nguyen Van A","phone":"0901234567","address":"","gender":"male"}
				""";
		mockMvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileCompletionRequired").value(false))
				.andExpect(jsonPath("$.data.phone").value("0901234567"));

		User refreshed = userRepository.findById(savedUser.getId()).orElseThrow();
		assertThat(refreshed.getProfileCompletionRequired()).isFalse();
		assertThat(ProfileCompletionSupport.isCustomerProfileComplete(refreshed)).isTrue();
	}

	@Test
	@Transactional
	void incompleteProfile_assertCustomerProfileCompleteThrows() {
		User incomplete = new User();
		incomplete.setName("A");
		incomplete.setEmail("incomplete@test.local");
		incomplete.setAuthProvider("google");
		incomplete.setStatus("active");
		incomplete.setDeleted(false);
		incomplete.setProfileCompletionRequired(true);
		incomplete = userRepository.save(incomplete);

		User finalIncomplete = incomplete;
		org.junit.jupiter.api.Assertions.assertThrows(
				scu.dn.used_cars_backend.common.exception.BusinessException.class,
				() -> ProfileCompletionSupport.assertCustomerProfileComplete(finalIncomplete));
	}

	private static String sha256Hex(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			throw new RuntimeException("Lỗi SHA-256", e);
		}
	}
}
