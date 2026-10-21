package com.fraudengine.api;

import java.time.Duration;
import java.time.Instant;

import com.fraudengine.TestcontainersConfiguration;
import com.fraudengine.engine.FraudRulesProperties;
import com.fraudengine.service.RuleConfigVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FraudApiControllerTest {

	private static final Instant T0 = Instant.parse("2026-10-19T10:00:00Z");

	@Autowired
	MockMvc mvc;

	@Value("${fraud.api-key}")
	String apiKey;

	@Autowired
	RuleConfigVersion configVersion;

	@Test
	void postEvaluatesThenReplayReturnsTheSameBodyWith200() throws Exception {
		var body = json("txn_api_1", "cus_api_1", 6_000_000, "GAMBLING", "CARD_PRESENT", "ZA", T0);

		var created = postTransaction(body).andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/v1/transactions/txn_api_1"))
			.andExpect(jsonPath("$.transactionId").value("txn_api_1"))
			.andExpect(jsonPath("$.decision").value("REVIEW"))
			.andExpect(jsonPath("$.totalScore").value(60))
			.andExpect(jsonPath("$.hits[*].ruleId").value(containsInAnyOrder("HIGH_AMOUNT", "HIGH_RISK_CATEGORY")))
			.andExpect(jsonPath("$.rulesErrored").isEmpty())
			.andExpect(jsonPath("$.configVersion").value(configVersion.version()))
			.andReturn()
			.getResponse()
			.getContentAsString();

		postTransaction(body).andExpect(status().isOk()).andExpect(content().json(created, true));
	}

	@Test
	void invalidRequestIs400WithTheFieldNames() throws Exception {
		var body = json("t".repeat(65), "cus_api_bad", 0, "GROCERIES", "CARD_PRESENT", "za", Instant.now().plus(Duration.ofMinutes(6)))
			.replace("\"ZAR\"", "\"zar\"");

		postTransaction(body).andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[*].field").value(
					containsInAnyOrder("transactionId", "amountMinor", "currency", "merchantCountry", "occurredAt")));
	}

	@Test
	void transactionIdThatIsNotUrlSafeIs400AndNotStored() throws Exception {
		postTransaction(json("txn 1", "cus_api_url", 10_000, "GROCERIES", "ONLINE", "ZA", T0))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(contains("transactionId")));
		perform(get("/api/v1/transactions/txn 1")).andExpect(status().isNotFound());
	}

	@Test
	void occurredAtSlightlyInTheFutureIsAccepted() throws Exception {
		postTransaction(json("txn_api_skew", "cus_api_skew", 10_000, "GROCERIES", "ONLINE", "ZA",
				Instant.now().plus(Duration.ofMinutes(4))))
			.andExpect(status().isCreated());
	}

	@Test
	void occurredAtOlderThanMaxEventAgeIs400(@Autowired FraudRulesProperties properties) throws Exception {
		var oldest = Instant.now().minus(properties.maxEventAge());
		postTransaction(json("txn_api_old", "cus_api_old", 10_000, "GROCERIES", "ONLINE", "ZA",
				oldest.minus(Duration.ofMinutes(1))))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[*].field").value(contains("occurredAt")));
		postTransaction(json("txn_api_late", "cus_api_old", 10_000, "GROCERIES", "ONLINE", "ZA",
				oldest.plus(Duration.ofMinutes(1))))
			.andExpect(status().isCreated());
	}

	@Test
	void unknownCurrencyIs400() throws Exception {
		postTransaction(json("txn_api_qqq", "cus_api_qqq", 10_000, "GROCERIES", "ONLINE", "ZA", T0)
			.replace("\"ZAR\"", "\"QQQ\""))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[*].field").value(contains("currency")));
		perform(get("/api/v1/transactions/txn_api_qqq")).andExpect(status().isNotFound());
	}

	@Test
	void unknownChannelIs400() throws Exception {
		postTransaction(json("txn_api_ch", "cus_api_ch", 10_000, "GROCERIES", "TELEPATHY", "ZA", T0))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void reusedIdWithDifferentDetailsIs409() throws Exception {
		postTransaction(json("txn_api_dup", "cus_api_dup", 10_000, "GROCERIES", "ONLINE", "ZA", T0))
			.andExpect(status().isCreated());

		postTransaction(json("txn_api_dup", "cus_api_dup", 20_000, "GROCERIES", "ONLINE", "ZA", T0))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void getReturnsTheTransactionAndItsEvaluation() throws Exception {
		postTransaction(json("txn_api_get", "cus_api_get", 10_000, "GROCERIES", "ONLINE", "ZA", T0))
			.andExpect(status().isCreated());

		perform(get("/api/v1/transactions/txn_api_get"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.transaction.customerId").value("cus_api_get"))
			.andExpect(jsonPath("$.transaction.amountMinor").value(10_000))
			.andExpect(jsonPath("$.transaction.occurredAt").value("2026-10-19T10:00:00Z"))
			.andExpect(jsonPath("$.receivedAt").isNotEmpty())
			.andExpect(jsonPath("$.evaluation.decision").value("CLEAN"))
			.andExpect(jsonPath("$.configVersion").value(configVersion.version()));
	}

	@Test
	void getUnknownIs404Problem() throws Exception {
		perform(get("/api/v1/transactions/nope"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void flagsListsNonCleanNewestFirstAndFilters() throws Exception {
		var customer = "cus_api_flags";
		postTransaction(json("txn_f_clean", customer, 10_000, "GROCERIES", "CARD_PRESENT", "ZA", T0));
		postTransaction(json("txn_f_review", customer, 6_000_000, "GAMBLING", "ONLINE", "ZA", T0.plusSeconds(60)));
		postTransaction(json("txn_f_hop", customer, 10_000, "GROCERIES", "CARD_PRESENT", "GB", T0.plusSeconds(600)));

		perform(get("/api/v1/flags").param("customerId", customer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].transaction.transactionId").value(
					contains("txn_f_hop", "txn_f_review")))
			.andExpect(jsonPath("$.content[0].evaluation.decision").value("HIGH_RISK"))
			.andExpect(jsonPath("$.page.totalElements").value(2));
		perform(get("/api/v1/flags").param("customerId", customer).param("ruleId", "COUNTRY_HOP"))
			.andExpect(jsonPath("$.content[*].transaction.transactionId").value(
					contains("txn_f_hop")));
		perform(get("/api/v1/flags").param("customerId", customer).param("decision", "REVIEW"))
			.andExpect(jsonPath("$.content[*].transaction.transactionId").value(
					contains("txn_f_review")));
		perform(get("/api/v1/flags").param("customerId", customer)
			.param("from", T0.plusSeconds(60).toString())
			.param("to", T0.plusSeconds(600).toString()))
			.andExpect(jsonPath("$.content[*].transaction.transactionId").value(
					contains("txn_f_review")));
	}

	@Test
	void flagsPageSizeOver100Is400() throws Exception {
		perform(get("/api/v1/flags").param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("size")));
	}

	@Test
	void rulesReturnsTheActiveConfig() throws Exception {
		perform(get("/api/v1/rules"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.configVersion").value(matchesPattern("[0-9a-f]{12}")))
			.andExpect(jsonPath("$.bands.reviewFrom").value(40))
			.andExpect(jsonPath("$.bands.highRiskFrom").value(80))
			.andExpect(jsonPath("$.bands.ordered").doesNotExist())
			.andExpect(jsonPath("$.rules.velocity.window").value("PT10M"))
			.andExpect(jsonPath("$.rules.velocity.enabled").value(true))
			.andExpect(jsonPath("$.rules.velocity.maxCount").value(5))
			.andExpect(jsonPath("$.rules.highAmount.thresholdsMinor.ZAR").value(5_000_000));
	}

	private ResultActions postTransaction(String body) throws Exception {
		return perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("X-API-Key", apiKey));
	}

	private static String json(String id, String customerId, long amountMinor, String category, String channel,
			String country, Instant occurredAt) {
		return """
				{"transactionId": "%s", "customerId": "%s", "amountMinor": %d, "currency": "ZAR",
				 "merchantId": "mer_1", "merchantCategory": "%s", "channel": "%s", "merchantCountry": "%s",
				 "occurredAt": "%s"}""".formatted(id, customerId, amountMinor, category, channel, country, occurredAt);
	}

}
