package scu.dn.used_cars_backend;

// Kiểm tra API công khai DEV 2: GET vehicles / catalog không cần JWT.

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehiclePublicApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listVehiclesWithoutToken() throws Exception {
		mockMvc.perform(get("/api/v1/vehicles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.meta.page").exists());
	}

	@Test
	void listVehiclesSortByPostingDate() throws Exception {
		mockMvc.perform(get("/api/v1/vehicles").param("sort", "postingDateDesc").param("size", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items").isArray());
	}

	@Test
	void listCategoriesWithoutToken() throws Exception {
		mockMvc.perform(get("/api/v1/catalog/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").isArray());
	}

}
