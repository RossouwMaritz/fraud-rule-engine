package com.fraudengine.engine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

	private static final FraudRulesProperties PROPERTIES = new FraudRulesProperties(
			new FraudRulesProperties.Bands(40, 80), null);

	private static final TransactionEvent TXN = new TransactionEvent("txn_1", "cus_1", 125_000, "ZAR", "mer_1",
			"GROCERIES", Channel.CARD_PRESENT, "ZA", Instant.parse("2026-10-19T10:00:00Z"));

	private static final CustomerHistory NO_HISTORY = new CustomerHistory(List.of(), Optional.empty(),
			new AmountStats(0, 0, 0));

	@Test
	void noHitsIsClean() {
		var evaluation = evaluate(new FakeRule("A", 0, true));

		assertThat(evaluation).isEqualTo(new Evaluation(0, Decision.CLEAN, List.of(), List.of()));
	}

	@ParameterizedTest
	@CsvSource({ "39, CLEAN", "40, REVIEW", "79, REVIEW", "80, HIGH_RISK" })
	void bandEdges(int score, Decision expected) {
		var evaluation = evaluate(new FakeRule("A", score, true));

		assertThat(evaluation.totalScore()).isEqualTo(score);
		assertThat(evaluation.decision()).isEqualTo(expected);
	}

	@Test
	void throwingRuleIsRecordedWhileOthersStillScore() {
		var evaluation = evaluate(new FakeRule("A", 30, true), throwingRule("BROKEN"), new FakeRule("B", 50, true));

		assertThat(evaluation.rulesErrored()).containsExactly("BROKEN");
		assertThat(evaluation.hits()).extracting(RuleHit::ruleId).containsExactly("A", "B");
		assertThat(evaluation.totalScore()).isEqualTo(80);
		assertThat(evaluation.decision()).isEqualTo(Decision.HIGH_RISK);
	}

	@Test
	void disabledRuleIsSkipped() {
		var evaluation = evaluate(new FakeRule("OFF", 90, false), new FakeRule("ON", 10, true));

		assertThat(evaluation.hits()).extracting(RuleHit::ruleId).containsExactly("ON");
		assertThat(evaluation.totalScore()).isEqualTo(10);
		assertThat(evaluation.decision()).isEqualTo(Decision.CLEAN);
		assertThat(evaluation.rulesErrored()).isEmpty();
	}

	private static Evaluation evaluate(FraudRule... rules) {
		return new RuleEngine(List.of(rules), PROPERTIES).evaluate(TXN, NO_HISTORY);
	}

	/** Hits with {@code score}, or doesn't fire when the score is 0. */
	private record FakeRule(String id, int score, boolean enabled) implements FraudRule {

		@Override
		public Optional<RuleHit> evaluate(TransactionEvent txn, CustomerHistory history) {
			return score == 0 ? Optional.empty() : Optional.of(new RuleHit(id, score, "fake"));
		}

	}

	private static FraudRule throwingRule(String id) {
		return new FraudRule() {

			@Override
			public String id() {
				return id;
			}

			@Override
			public boolean enabled() {
				return true;
			}

			@Override
			public Optional<RuleHit> evaluate(TransactionEvent txn, CustomerHistory history) {
				throw new IllegalStateException("boom");
			}

		};
	}

}
