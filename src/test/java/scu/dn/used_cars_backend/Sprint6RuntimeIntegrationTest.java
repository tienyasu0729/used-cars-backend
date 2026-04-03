package scu.dn.used_cars_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.entity.Role;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.repository.RoleRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.UserRoleRepository;
import scu.dn.used_cars_backend.security.JwtService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Sprint6RuntimeIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserRoleRepository userRoleRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String adminToken;
	private String customerToken;

	@BeforeEach
	void seedUsers() {
		Role adminRole = roleRepository.findByNameIgnoreCase("Admin").orElseGet(() -> {
			Role r = new Role();
			r.setName("Admin");
			r.setDescription("Admin");
			r.setSystemRole(true);
			return roleRepository.save(r);
		});
		Role custRole = roleRepository.findByNameIgnoreCase("Customer").orElseGet(() -> {
			Role r = new Role();
			r.setName("Customer");
			r.setDescription("KH");
			r.setSystemRole(true);
			return roleRepository.save(r);
		});
		User admin = userRepository.save(buildUser("s6-admin-rt@test.local", "S6 Admin"));
		saveLink(admin, adminRole);
		User cust = userRepository.save(buildUser("s6-cust-rt@test.local", "S6 Customer"));
		saveLink(cust, custRole);
		adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), "Admin");
		customerToken = jwtService.generateToken(cust.getId(), cust.getEmail(), "Customer");
	}

	private User buildUser(String email, String name) {
		User u = new User();
		u.setName(name);
		u.setEmail(email);
		u.setAuthProvider("local");
		u.setStatus("active");
		u.setDeleted(false);
		u.setPasswordChangeRequired(false);
		u.setPasswordHash(passwordEncoder.encode("secret123"));
		return u;
	}

	private void saveLink(User u, Role r) {
		UserRole ur = new UserRole();
		ur.setUser(u);
		ur.setRole(r);
		userRoleRepository.save(ur);
	}

	@Test
	void admin_catalog_config_logs_dashboard_manager_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/catalog/brands").param("page", "0").param("size", "10")
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.content").isArray());
		mockMvc.perform(get("/api/v1/admin/config").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		mockMvc.perform(get("/api/v1/admin/logs").header("Authorization", "Bearer " + adminToken)
						.param("page", "0").param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meta").exists());
		mockMvc.perform(get("/api/v1/admin/dashboard/stats").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").exists());
		mockMvc.perform(get("/api/v1/manager/reports").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.salesByBrand").isArray())
				.andExpect(jsonPath("$.data.topModels").isArray());
	}

	@Test
	void customer_forbidden_on_admin_and_manager_reports() throws Exception {
		mockMvc.perform(get("/api/v1/admin/catalog/brands").header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/manager/reports").header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void sales_staff_forbidden_on_admin_catalog() throws Exception {
		Role sales = roleRepository.findByNameIgnoreCase("SalesStaff").orElseGet(() -> {
			Role r = new Role();
			r.setName("SalesStaff");
			r.setDescription("NV");
			r.setSystemRole(true);
			return roleRepository.save(r);
		});
		User staff = userRepository.save(buildUser("s6-sales-rt@test.local", "S6 Sales"));
		saveLink(staff, sales);
		String token = jwtService.generateToken(staff.getId(), staff.getEmail(), "SalesStaff");
		mockMvc.perform(get("/api/v1/admin/catalog/brands").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void config_put_then_get_roundtrip() throws Exception {
		String key = "sprint6_rt_verify";
		String body = objectMapper.writeValueAsString(java.util.List.of(java.util.Map.of("key", key, "value", "ok")));
		mockMvc.perform(put("/api/v1/admin/config").header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());
		MvcResult res = mockMvc.perform(get("/api/v1/admin/config").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
		JsonNode data = root.get("data");
		assertThat(data.isArray()).isTrue();
		boolean found = false;
		for (JsonNode e : data) {
			if (key.equals(e.get("key").asText())) {
				assertThat(e.get("value").asText()).isEqualTo("ok");
				found = true;
				break;
			}
		}
		assertThat(found).isTrue();
	}

	@Test
	void home_banners_admin_list_and_public_catalog() throws Exception {
		mockMvc.perform(get("/api/v1/admin/home-banners").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
		mockMvc.perform(get("/api/v1/catalog/home-banners"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void vnpay_query_and_refund_authz_rules_work() throws Exception {
		String body = objectMapper.writeValueAsString(java.util.Map.of("orderPaymentId", 999999L));
		mockMvc.perform(post("/api/v1/payment/vnpay/query")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/payment/vnpay/refund")
						.header("Authorization", "Bearer " + customerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden());
		Role sales = roleRepository.findByNameIgnoreCase("SalesStaff").orElseGet(() -> {
			Role r = new Role();
			r.setName("SalesStaff");
			r.setDescription("NV");
			r.setSystemRole(true);
			return roleRepository.save(r);
		});
		User staff = userRepository.save(buildUser("s6-sales-pay@test.local", "S6 Sales Pay"));
		saveLink(staff, sales);
		String staffToken = jwtService.generateToken(staff.getId(), staff.getEmail(), "SalesStaff");
		mockMvc.perform(post("/api/v1/payment/vnpay/refund")
						.header("Authorization", "Bearer " + staffToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden());
		int adminQueryStatus = mockMvc.perform(post("/api/v1/payment/vnpay/query")
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andReturn()
				.getResponse()
				.getStatus();
		assertThat(adminQueryStatus).isNotEqualTo(401).isNotEqualTo(403);
	}

	@Test
	void list_order_payments_forbidden_for_customer_not_found_for_admin() throws Exception {
		mockMvc.perform(get("/api/v1/payment/orders/999999996/payments").header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/payment/orders/999999996/payments").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNotFound());
	}
}
