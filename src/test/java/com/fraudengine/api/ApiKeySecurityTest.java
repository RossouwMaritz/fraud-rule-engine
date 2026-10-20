package com.fraudengine.api;

import com.fraudengine.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ApiKeySecurityTest {

	@Autowired
	MockMvc mvc;

	@Value("${fraud.api-key}")
	String apiKey;

	@Test
	void noKeyIs401Problem() throws Exception {
		mvc.perform(get("/api/v1/rules"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void wrongKeyIs401() throws Exception {
		mvc.perform(get("/api/v1/rules").header("X-API-Key", apiKey + "x")).andExpect(status().isUnauthorized());
	}

	@Test
	void postWithoutKeyIs401NotCsrf403() throws Exception {
		mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rightKeyIs200() throws Exception {
		mvc.perform(get("/api/v1/rules").header("X-API-Key", apiKey)).andExpect(status().isOk());
	}

	@Test
	void healthNeedsNoKey() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
	}

}
