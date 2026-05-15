package scu.dn.used_cars_backend.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Validates: Requirements 1.2, 1.3, 1.4, 1.5
 *
 * Property 1: Bug Condition - Duplicate Email/Phone Returns Field-Level Errors
 *
 * Bug Condition: isBugCondition(input) WHERE
 *   userRepository.existsByEmailIgnoreCaseAndDeletedFalse(input.email)
 *   OR userRepository.existsByPhoneIgnoreCaseAndDeletedFalse(input.phone)
 *
 * Expected behavior (after fix):
 *   response.status = 400
 *   response.errorCode = "VALIDATION_FAILED"
 *   response.errors IS NOT NULL
 *   errors[] contains {field:"email"} when email is duplicate
 *   errors[] contains {field:"phone"} when phone is duplicate
 *   errors[] contains both when both are duplicate
 */
@JqwikSpringSupport
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationDuplicateValidationPBTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

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
		if (!userRepository.existsByPhoneIgnoreCaseAndDeletedFalse(phone)) {
			User u = new User();
			u.setName("Existing User Phone");
			u.setEmail("phone_holder_" + phone + "@test.com");
			u.setPhone(phone);
			u.setPasswordHash(passwordEncoder.encode("password123"));
			u.setDeleted(false);
			userRepository.save(u);
		}
	}

	@Property(tries = 5)
	@Label("Bug Condition: duplicate email returns HTTP 400 + VALIDATION_FAILED + errors[{field:email}]")
	void duplicateEmailReturnsFieldLevelError(
			@ForAll("duplicateEmailInputs") DuplicateInput input) throws Exception {

		ensureExistingUser(input.existingEmail(), "0900000001");

		String requestBody = """
				{"email":"%s","phone":"%s"}
				""".formatted(input.existingEmail(), input.newPhone());

		MvcResult result = mockMvc.perform(
				post("/api/v1/auth/register/request-otp")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andReturn();

		int status = result.getResponse().getStatus();
		String responseBody = result.getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(responseBody);

		assertThat(status).as("HTTP status should be 400").isEqualTo(400);
		assertThat(root.get("errorCode").asText())
				.as("errorCode should be VALIDATION_FAILED")
				.isEqualTo("VALIDATION_FAILED");
		assertThat(root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty())
				.as("errors[] should be present and non-empty")
				.isTrue();

		boolean hasEmailError = false;
		for (JsonNode err : root.get("errors")) {
			if ("email".equals(err.get("field").asText())) {
				hasEmailError = true;
				break;
			}
		}
		assertThat(hasEmailError)
				.as("errors[] should contain entry with field='email'")
				.isTrue();
	}

	@Property(tries = 5)
	@Label("Bug Condition: duplicate phone returns HTTP 400 + VALIDATION_FAILED + errors[{field:phone}]")
	void duplicatePhoneReturnsFieldLevelError(
			@ForAll("duplicatePhoneInputs") DuplicateInput input) throws Exception {

		ensureExistingUser("existing_phone_test@example.com", input.existingPhone());

		String requestBody = """
				{"email":"%s","phone":"%s"}
				""".formatted(input.newEmail(), input.existingPhone());

		MvcResult result = mockMvc.perform(
				post("/api/v1/auth/register/request-otp")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andReturn();

		int status = result.getResponse().getStatus();
		String responseBody = result.getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(responseBody);

		assertThat(status).as("HTTP status should be 400, not 409").isEqualTo(400);
		assertThat(root.get("errorCode").asText())
				.as("errorCode should be VALIDATION_FAILED, not STAFF_PHONE_EXISTS")
				.isEqualTo("VALIDATION_FAILED");
		assertThat(root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty())
				.as("errors[] should be present and non-empty")
				.isTrue();

		boolean hasPhoneError = false;
		for (JsonNode err : root.get("errors")) {
			if ("phone".equals(err.get("field").asText())) {
				hasPhoneError = true;
				break;
			}
		}
		assertThat(hasPhoneError)
				.as("errors[] should contain entry with field='phone'")
				.isTrue();
	}

	@Property(tries = 5)
	@Label("Bug Condition: both duplicate returns HTTP 400 + VALIDATION_FAILED + errors with both fields")
	void bothDuplicateReturnsAllFieldErrors(
			@ForAll("bothDuplicateInputs") DuplicateInput input) throws Exception {

		ensureExistingUser(input.existingEmail(), input.existingPhone());

		String requestBody = """
				{"email":"%s","phone":"%s"}
				""".formatted(input.existingEmail(), input.existingPhone());

		MvcResult result = mockMvc.perform(
				post("/api/v1/auth/register/request-otp")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andReturn();

		int status = result.getResponse().getStatus();
		String responseBody = result.getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(responseBody);

		assertThat(status).as("HTTP status should be 400").isEqualTo(400);
		assertThat(root.get("errorCode").asText())
				.as("errorCode should be VALIDATION_FAILED")
				.isEqualTo("VALIDATION_FAILED");
		assertThat(root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty())
				.as("errors[] should be present and non-empty")
				.isTrue();

		boolean hasEmailError = false;
		boolean hasPhoneError = false;
		for (JsonNode err : root.get("errors")) {
			String field = err.get("field").asText();
			if ("email".equals(field)) hasEmailError = true;
			if ("phone".equals(field)) hasPhoneError = true;
		}
		assertThat(hasEmailError)
				.as("errors[] should contain entry with field='email'")
				.isTrue();
		assertThat(hasPhoneError)
				.as("errors[] should contain entry with field='phone'")
				.isTrue();
	}

	@Provide
	Arbitrary<DuplicateInput> duplicateEmailInputs() {
		Arbitrary<String> emails = Arbitraries.of(
				"dup_email_1@test.com", "dup_email_2@test.com",
				"dup_email_3@test.com", "DUP_EMAIL_1@TEST.COM");
		Arbitrary<String> newPhones = Arbitraries.of(
				"0911111111", "0922222222", "0933333333");
		return Combinators.combine(emails, newPhones)
				.as((email, phone) -> new DuplicateInput(email, null, null, phone));
	}

	@Provide
	Arbitrary<DuplicateInput> duplicatePhoneInputs() {
		Arbitrary<String> newEmails = Arbitraries.of(
				"new_phone_test_1@test.com", "new_phone_test_2@test.com",
				"new_phone_test_3@test.com");
		Arbitrary<String> phones = Arbitraries.of(
				"0901234567", "0907654321", "0909876543");
		return Combinators.combine(newEmails, phones)
				.as((email, phone) -> new DuplicateInput(null, phone, email, null));
	}

	@Provide
	Arbitrary<DuplicateInput> bothDuplicateInputs() {
		Arbitrary<String> emails = Arbitraries.of(
				"both_dup_1@test.com", "both_dup_2@test.com");
		Arbitrary<String> phones = Arbitraries.of(
				"0941111111", "0942222222");
		return Combinators.combine(emails, phones)
				.as((email, phone) -> new DuplicateInput(email, phone, null, null));
	}

	record DuplicateInput(String existingEmail, String existingPhone,
						  String newEmail, String newPhone) {}
}
