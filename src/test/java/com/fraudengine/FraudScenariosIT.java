package com.fraudengine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.fraudengine.engine.Decision;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.fraudengine.engine.Decision.CLEAN;
import static com.fraudengine.engine.Decision.HIGH_RISK;
import static com.fraudengine.engine.Decision.REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Planted fraud and legitimate controls, posted through the real API in time order. Each scenario has its own
 * customer, so no scenario's history can trigger another's rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FraudScenariosIT {

	// R300–R600, one a day: the steady history the amount scenarios compare against.
	private static final long[] STEADY_AMOUNTS = { 30_000, 45_000, 60_000, 38_000, 52_000, 41_000, 57_000, 33_000,
			48_000, 60_000, 35_000, 44_000, 55_000, 30_000, 50_000 };

	@Autowired
	MockMvc mvc;

	@Value("${fraud.api-keys}") // one key in the test profile
	String apiKey;

	@Test
	void cardTestingTripsVelocityFromTheSixthCharge() throws Exception {
		var customer = new Customer("cus_sc_cardtest");
		var start = Instant.parse("2026-10-01T02:13:00Z");
		// 7 small charges 40 seconds apart: 4 minutes end to end.
		for (int i = 0; i < 7; i++) {
			var expected = i < 5 ? CLEAN : REVIEW;
			var rules = i < 5 ? new String[0] : new String[] { "VELOCITY" };
			customer.pay(500 + 100L * i, "mer_webshop", "DIGITAL_GOODS", "ONLINE", "US", start.plusSeconds(40L * i))
				.expect(expected, rules);
		}
	}

	// The same burst, arriving newest first: a feed that retries or batches. The last 2 to arrive are flagged.
	@Test
	void cardTestingArrivingInReverseOrderIsStillFlagged() throws Exception {
		var customer = new Customer("cus_sc_cardtest_late");
		var start = Instant.parse("2026-10-01T02:13:00Z");
		for (int i = 6; i >= 0; i--) {
			var expected = i > 1 ? CLEAN : REVIEW;
			var rules = i > 1 ? new String[0] : new String[] { "VELOCITY" };
			customer.pay(500 + 100L * i, "mer_webshop", "DIGITAL_GOODS", "ONLINE", "US", start.plusSeconds(40L * i))
				.expect(expected, rules);
		}
	}

	// A family on one account: 2 cards, 4 charges each in 10 minutes, is not a burst. 3 more on one card then is.
	@Test
	void velocityCountsEachCardOnItsOwn() throws Exception {
		var cardA = new Customer("cus_sc_family", "card_sc_family_a");
		var cardB = new Customer("cus_sc_family", "card_sc_family_b");
		var start = Instant.parse("2026-10-01T09:00:00Z");
		for (int i = 0; i < 8; i++) {
			var card = i % 2 == 0 ? cardA : cardB;
			card.pay(500 + 100L * (i % 3), "mer_webshop", "DIGITAL_GOODS", "ONLINE", "ZA", start.plusSeconds(60L * i))
				.expect(CLEAN);
		}
		cardA.pay(600, "mer_webshop", "DIGITAL_GOODS", "ONLINE", "ZA", start.plusSeconds(480)).expect(CLEAN);
		cardA.pay(500, "mer_webshop", "DIGITAL_GOODS", "ONLINE", "ZA", start.plusSeconds(500))
			.expect(REVIEW, "VELOCITY");
		cardA.pay(700, "mer_webshop", "DIGITAL_GOODS", "ONLINE", "ZA", start.plusSeconds(520))
			.expect(REVIEW, "VELOCITY");
	}

	@Test
	void lateCardPresentInZaBeforeAStoredGbOneIsHighRisk() throws Exception {
		var customer = new Customer("cus_sc_hop_late");
		var gb = Instant.parse("2026-10-02T10:30:00Z");
		customer.pay(120_000, "mer_london", "RETAIL", "CARD_PRESENT", "GB", gb).expect(CLEAN);
		customer.pay(45_000, "mer_grocer_1", "GROCERIES", "CARD_PRESENT", "ZA", gb.minus(Duration.ofMinutes(30)))
			.expect(HIGH_RISK, "COUNTRY_HOP");
	}

	@Test
	void cardPresentInZaThenGb40MinutesLaterIsHighRisk() throws Exception {
		var customer = new Customer("cus_sc_hop");
		var start = Instant.parse("2026-10-02T08:00:00Z");
		customer.pay(45_000, "mer_grocer_1", "GROCERIES", "CARD_PRESENT", "ZA", start).expect(CLEAN);
		customer.pay(120_000, "mer_london", "RETAIL", "CARD_PRESENT", "GB", start.plus(Duration.ofMinutes(40)))
			.expect(HIGH_RISK, "COUNTRY_HOP");
	}

	@Test
	void sixtyThousandRandAtAGamblingMerchantIsReview() throws Exception {
		new Customer("cus_sc_gambling")
			.pay(6_000_000, "mer_casino", "GAMBLING", "ONLINE", "ZA", Instant.parse("2026-10-03T22:00:00Z"))
			.expect(REVIEW, "HIGH_AMOUNT", "HIGH_RISK_CATEGORY");
	}

	@Test
	void spikeAgainstASteadyHistoryIsAnAmountAnomaly() throws Exception {
		var customer = new Customer("cus_sc_spike");
		var start = Instant.parse("2026-09-01T17:00:00Z");
		customer.seedSteadyHistory(start);
		var spike = start.plus(Duration.ofDays(15));
		customer.pay(750_000, "mer_electronics", "ELECTRONICS", "ONLINE", "ZA", spike)
			.expect(REVIEW, "AMOUNT_ANOMALY");
		// Paying the flagged amount again an hour later is not a repeat payment, so it stays flagged.
		customer.pay(750_000, "mer_electronics", "ELECTRONICS", "ONLINE", "ZA", spike.plus(Duration.ofHours(1)))
			.expect(REVIEW, "AMOUNT_ANOMALY");
	}

	// The flagged spike must not pass as a repeat payment a month later, or the baseline would learn it.
	@Test
	void flaggedSpikeRepeatedAMonthLaterIsStillFlagged() throws Exception {
		var customer = new Customer("cus_sc_spike_repeat");
		var start = Instant.parse("2026-08-01T17:00:00Z");
		customer.seedSteadyHistory(start);
		var spike = start.plus(Duration.ofDays(15));
		customer.pay(750_000, "mer_electronics", "ELECTRONICS", "ONLINE", "ZA", spike)
			.expect(REVIEW, "AMOUNT_ANOMALY");
		// Fresh history, so the second spike still has 10+ transactions in its 30-day window.
		customer.seedSteadyHistory(spike.plus(Duration.ofDays(1)));
		customer.pay(750_000, "mer_electronics", "ELECTRONICS", "ONLINE", "ZA", spike.plus(Duration.ofDays(30)))
			.expect(REVIEW, "AMOUNT_ANOMALY");
	}

	@Test
	void monthlyRentInA31DayMonthIsClean() throws Exception {
		var customer = new Customer("cus_sc_rent");
		var lastRent = Instant.parse("2026-08-25T06:00:00Z");
		customer.pay(950_000, "mer_landlord", "RENT", "ONLINE", "ZA", lastRent).expect(CLEAN);
		// 12 everyday payments of R280–R850. Without the repeat check, these alone would make the rent an anomaly.
		long[] amounts = { 28_000, 85_000, 41_000, 63_000, 35_000, 72_000, 30_000, 54_000, 47_000, 81_000, 39_000,
				66_000 };
		for (int i = 0; i < amounts.length; i++) {
			customer.pay(amounts[i], "mer_grocer_" + (i % 3), "GROCERIES", "CARD_PRESENT", "ZA",
					lastRent.plus(Duration.ofDays(2 + 2L * i)).plus(Duration.ofHours(11)))
				.expect(CLEAN);
		}
		// 31 days later: last month's rent is outside the 30-day stats window, but one billing cycle back.
		customer.pay(950_000, "mer_landlord", "RENT", "ONLINE", "ZA", Instant.parse("2026-09-25T06:00:00Z"))
			.expect(CLEAN);
	}

	@Test
	void normalGroceryShopIsClean() throws Exception {
		var customer = new Customer("cus_sc_grocery");
		var start = Instant.parse("2026-09-01T17:00:00Z");
		customer.seedSteadyHistory(start);
		customer.pay(58_000, "mer_grocer_1", "GROCERIES", "CARD_PRESENT", "ZA", start.plus(Duration.ofDays(15)))
			.expect(CLEAN);
	}

	@Test
	void onlineUsPurchaseStraightAfterCardPresentInZaIsClean() throws Exception {
		var customer = new Customer("cus_sc_online");
		var start = Instant.parse("2026-10-04T12:00:00Z");
		customer.pay(35_000, "mer_grocer_1", "GROCERIES", "CARD_PRESENT", "ZA", start).expect(CLEAN);
		customer.pay(89_900, "mer_us_store", "RETAIL", "ONLINE", "US", start.plus(Duration.ofMinutes(5)))
			.expect(CLEAN);
	}

	@Test
	void realTripZaThenGb11HoursLaterIsClean() throws Exception {
		var customer = new Customer("cus_sc_trip");
		var start = Instant.parse("2026-10-05T07:00:00Z");
		customer.pay(25_000, "mer_airport_za", "RESTAURANTS", "CARD_PRESENT", "ZA", start).expect(CLEAN);
		customer.pay(60_000, "mer_london", "RESTAURANTS", "CARD_PRESENT", "GB", start.plus(Duration.ofHours(11)))
			.expect(CLEAN);
	}

	private final class Customer {

		private final String id;

		private final String cardId;

		private int count;

		Customer(String id) {
			this(id, id + "_card");
		}

		Customer(String id, String cardId) {
			this.id = id;
			this.cardId = cardId;
		}

		/** 15 card-present groceries, one a day from {@code start}, every one CLEAN. */
		void seedSteadyHistory(Instant start) throws Exception {
			for (int i = 0; i < STEADY_AMOUNTS.length; i++) {
				pay(STEADY_AMOUNTS[i], "mer_grocer_" + (i % 3), "GROCERIES", "CARD_PRESENT", "ZA",
						start.plus(Duration.ofDays(i)))
					.expect(CLEAN);
			}
		}

		Posted pay(long amountMinor, String merchantId, String category, String channel, String country,
				Instant occurredAt) throws Exception {
			// Named after the card, so two cards of one customer never reuse an ID.
			var transactionId = "txn_" + cardId + "_" + ++count;
			var body = """
					{"transactionId": "%s", "customerId": "%s", "cardId": "%s", "amountMinor": %d, "currency": "ZAR",
					 "merchantId": "%s", "merchantCategory": "%s", "channel": "%s", "merchantCountry": "%s",
					 "occurredAt": "%s"}""".formatted(transactionId, id, cardId, amountMinor, merchantId, category,
					channel, country, occurredAt);
			var response = mvc
				.perform(post("/api/v1/transactions").header("X-API-Key", apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
			return new Posted(transactionId, response);
		}

	}

	private record Posted(String transactionId, String response) {

		void expect(Decision decision, String... ruleIds) {
			assertThat(JsonPath.<String>read(response, "$.decision")).as(transactionId).isEqualTo(decision.name());
			assertThat(JsonPath.<List<String>>read(response, "$.hits[*].ruleId")).as(transactionId)
				.containsExactlyInAnyOrder(ruleIds);
			assertThat(JsonPath.<List<String>>read(response, "$.rulesErrored")).as(transactionId).isEmpty();
		}

	}

}
