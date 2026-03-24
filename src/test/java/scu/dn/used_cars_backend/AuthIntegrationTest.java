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

import scu.dn.used_cars_backend.entity.Role;
import scu.dn.used_cars_backend.repository.RoleRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
