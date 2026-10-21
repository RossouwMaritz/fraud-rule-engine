package com.fraudengine.persistence;

import java.time.Instant;
import java.util.List;

import com.fraudengine.TestcontainersConfiguration;
import com.fraudengine.engine.AmountStats;
import com.fraudengine.engine.Channel;
import com.fraudengine.engine.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class TransactionRepositoryTest {

	private static final Instant T0 = Instant.parse("2026-10-19T10:00:00Z");

	@Autowired
	TransactionRepository repository;

	@Autowired
	TestEntityManager em;

	@Test
	void savesTransactionWithHitsAndReadsItBack() {
		var txn = new TransactionEntity("txn_1", "cus_1", 6_000_000, "ZAR", "mer_1", "GAMBLING", Channel.CARD_PRESENT,
				"ZA", T0, 60, Decision.REVIEW, List.of("VELOCITY"), "unknown");
		txn.addHit(new RuleHitEntity("HIGH_AMOUNT", 40, "R60000.00 is over the R50000.00 limit"));
		txn.addHit(new RuleHitEntity("HIGH_RISK_CATEGORY", 20, "GAMBLING is a high-risk category"));
		repository.save(txn);
		em.flush();
		em.clear();

		var stored = repository.findById("txn_1").orElseThrow();

		assertThat(stored.getAmountMinor()).isEqualTo(6_000_000);
		assertThat(stored.getCurrency()).isEqualTo("ZAR");
		assertThat(stored.getMerchantCountry()).isEqualTo("ZA");
		assertThat(stored.getChannel()).isEqualTo(Channel.CARD_PRESENT);
		assertThat(stored.getDecision()).isEqualTo(Decision.REVIEW);
		assertThat(stored.getRulesErrored()).containsExactly("VELOCITY");
		assertThat(stored.getConfigVersion()).isEqualTo("unknown");
		assertThat(stored.getReceivedAt()).isNotNull();
		assertThat(stored.getHits()).extracting(RuleHitEntity::getRuleId, RuleHitEntity::getScore)
			.containsExactlyInAnyOrder(tuple("HIGH_AMOUNT", 40), tuple("HIGH_RISK_CATEGORY", 20));
	}

	@Test
	void savingADuplicateIdFailsInsteadOfOverwriting() {
		saveAll(txn("txn_1", "cus_1", 100, Channel.ONLINE, "ZA", T0));

		assertThatThrownBy(() -> repository.saveAndFlush(txn("txn_1", "cus_1", 999, Channel.ONLINE, "GB", T0)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void amountStatsMatchKnownNumbers() {
		// In window: 100, 200, 300, 400 -> mean 250, sample stddev sqrt(50000 / 3).
		saveAll(txn("a", "cus_1", 100, Channel.ONLINE, "ZA", T0.minusSeconds(4000)),
				txn("b", "cus_1", 200, Channel.ONLINE, "ZA", T0.minusSeconds(3000)),
				txn("c", "cus_1", 300, Channel.ONLINE, "ZA", T0.minusSeconds(2000)),
				txn("d", "cus_1", 400, Channel.ONLINE, "ZA", T0.minusSeconds(1000)),
				// Excluded: at the exclusive end, before the window, another customer, flagged, and another currency.
				txn("e", "cus_1", 9_999, Channel.ONLINE, "ZA", T0),
				txn("f", "cus_1", 9_999, Channel.ONLINE, "ZA", T0.minusSeconds(9000)),
				txn("g", "cus_2", 9_999, Channel.ONLINE, "ZA", T0.minusSeconds(1000)),
				new TransactionEntity("h", "cus_1", 9_999, "ZAR", "mer_1", "GAMBLING", Channel.ONLINE, "ZA",
						T0.minusSeconds(1000), 50, Decision.REVIEW, List.of(), "unknown"),
				new TransactionEntity("i", "cus_1", 9_999, "USD", "mer_1", "GAMBLING", Channel.ONLINE, "ZA",
						T0.minusSeconds(1000), 0, Decision.CLEAN, List.of(), "unknown"));

		var stats = repository.amountStats("cus_1", "ZAR", T0.minusSeconds(5000), T0);

		assertThat(stats.count()).isEqualTo(4);
		assertThat(stats.mean()).isCloseTo(250.0, within(1e-9));
		assertThat(stats.stddev()).isCloseTo(Math.sqrt(50_000.0 / 3), within(1e-9));
	}

	@Test
	void amountStatsWithNoHistoryIsZero() {
		assertThat(repository.amountStats("nobody", "ZAR", T0.minusSeconds(5000), T0)).isEqualTo(new AmountStats(0, 0, 0));
	}

	@Test
	void findRecentReturnsWindowNewestFirst() {
		saveAll(txn("old", "cus_1", 100, Channel.ONLINE, "ZA", T0.minusSeconds(700)),
				txn("from", "cus_1", 100, Channel.ONLINE, "ZA", T0.minusSeconds(600)),
				txn("mid", "cus_1", 100, Channel.ONLINE, "ZA", T0.minusSeconds(300)),
				txn("to", "cus_1", 100, Channel.ONLINE, "ZA", T0),
				txn("other", "cus_2", 100, Channel.ONLINE, "ZA", T0));

		assertThat(repository.findRecent("cus_1", T0.minusSeconds(600), T0))
			.extracting(TransactionEntity::getTransactionId)
			.containsExactly("to", "mid", "from");
	}

	@Test
	void findPreviousCardPresentIncludesSameTimeAndSkipsOnlineAndLaterTransactions() {
		saveAll(txn("cp_old", "cus_1", 100, Channel.CARD_PRESENT, "ZA", T0.minusSeconds(3000)),
				txn("cp_prev", "cus_1", 100, Channel.CARD_PRESENT, "GB", T0.minusSeconds(2000)),
				txn("online", "cus_1", 100, Channel.ONLINE, "US", T0.minusSeconds(1000)),
				txn("cp_same_time", "cus_1", 100, Channel.CARD_PRESENT, "FR", T0),
				txn("cp_later", "cus_1", 100, Channel.CARD_PRESENT, "DE", T0.plusSeconds(1)));

		assertThat(repository.findPreviousCardPresent("cus_1", T0)).map(TransactionEntity::getTransactionId)
			.contains("cp_same_time");
		assertThat(repository.findPreviousCardPresent("cus_1", T0.minusSeconds(1))).map(TransactionEntity::getTransactionId)
			.contains("cp_prev");
		assertThat(repository.findPreviousCardPresent("cus_1", T0.minusSeconds(3001))).isEmpty();
	}

	@Test
	void findNextCardPresentSkipsSameTimeOnlineAndEarlierTransactions() {
		saveAll(txn("cp_earlier", "cus_1", 100, Channel.CARD_PRESENT, "ZA", T0.minusSeconds(1)),
				txn("cp_same_time", "cus_1", 100, Channel.CARD_PRESENT, "FR", T0),
				txn("online", "cus_1", 100, Channel.ONLINE, "US", T0.plusSeconds(1000)),
				txn("cp_next", "cus_1", 100, Channel.CARD_PRESENT, "GB", T0.plusSeconds(2000)),
				txn("cp_later", "cus_1", 100, Channel.CARD_PRESENT, "DE", T0.plusSeconds(3000)),
				txn("other", "cus_2", 100, Channel.CARD_PRESENT, "NL", T0.plusSeconds(1)));

		assertThat(repository.findNextCardPresent("cus_1", T0)).map(TransactionEntity::getTransactionId)
			.contains("cp_next");
		assertThat(repository.findNextCardPresent("cus_1", T0.plusSeconds(3000))).isEmpty();
	}

	private void saveAll(TransactionEntity... txns) {
		repository.saveAll(List.of(txns));
		em.flush();
		em.clear();
	}

	private static TransactionEntity txn(String id, String customerId, long amountMinor, Channel channel,
			String country, Instant occurredAt) {
		return new TransactionEntity(id, customerId, amountMinor, "ZAR", "mer_1", "GAMBLING", channel, country,
				occurredAt, 0, Decision.CLEAN, List.of(), "unknown");
	}

}
