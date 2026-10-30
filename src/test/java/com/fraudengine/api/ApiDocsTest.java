package com.fraudengine.api;

import com.fraudengine.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The docs switched on, as compose does for local use. ApiKeySecurityTest covers them off. */
@SpringBootTest(properties = "fraud.docs-enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ApiDocsTest {

	@Autowired
	MockMvc mvc;

	@Test
	void apiDocsNeedNoKeyAndDeclareIt() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.components.securitySchemes.apiKey.name").value("X-API-Key"))
			.andExpect(jsonPath("$.paths['/api/v1/flags'].get.security[0].apiKey").exists());
		mvc.perform(get("/swagger-ui.html")).andExpect(header().string("Location", endsWith("/swagger-ui/index.html")));
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

}
