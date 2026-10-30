package com.fraudengine.api;

import com.fraudengine.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A burst of 2 and almost no refill, so the third request in a row is over the limit. */
@SpringBootTest(properties = { "fraud.api-keys=" + RateLimitTest.KEY_A + "," + RateLimitTest.KEY_B,
		"fraud.rate-limit.per-second=0.001", "fraud.rate-limit.burst=2" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RateLimitTest {

	static final String KEY_A = "rate-limit-key-a-0123456789abcdef";

	static final String KEY_B = "rate-limit-key-b-0123456789abcdef";

	@Autowired
	MockMvc mvc;

	@Test
	void aBurstPastTheLimitGets429ForThatKeyOnly() throws Exception {
		for (var i = 0; i < 2; i++) {
			mvc.perform(get("/api/v1/rules").header("X-API-Key", KEY_A)).andExpect(status().isOk());
		}
		mvc.perform(get("/api/v1/rules").header("X-API-Key", KEY_A))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "1000"))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(429));
		mvc.perform(get("/api/v1/rules").header("X-API-Key", KEY_B)).andExpect(status().isOk());
		// A request without a valid key isn't counted, so it still gets 401, not 429.
		mvc.perform(get("/api/v1/rules").header("X-API-Key", KEY_A + "x")).andExpect(status().isUnauthorized());
	}

}
