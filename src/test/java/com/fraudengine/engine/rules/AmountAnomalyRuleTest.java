package com.fraudengine.engine.rules;

import java.util.List;
import java.util.Optional;

import com.fraudengine.engine.AmountStats;
import com.fraudengine.engine.CustomerHistory;
import com.fraudengine.engine.RuleHit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.fraudengine.engine.rules.TestEvents.PROPERTIES;
import static com.fraudengine.engine.rules.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;

class AmountAnomalyRuleTest {

	private final AmountAnomalyRule rule = new AmountAnomalyRule(PROPERTIES);

	// Mean R450 and stddev R50 give a limit of R450 + 3 x R50 = R600.
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"10 | 45000 | 5000 | 59999 |", // just under the limit
			"10 | 45000 | 5000 | 60000 |", // exactly at it
			"10 | 45000 | 5000 | 60001 | ZAR 600.01 is over ZAR 600.00: the mean of the last 30 days (ZAR 450.00) plus 3 standard deviations (ZAR 50.00), from 10 transactions",
			" 9 | 45000 | 5000 | 99999 |", // one short of the minimum history
			" 0 |     0 |    0 | 99999 |", // no history at all
			"20 | 45000 |    0 | 99999 |" // every past amount the same: stddev 0
	})
	void firesOnlyOverMeanPlusStddevsWithEnoughHistory(long count, double mean, double stddev, long amountMinor,
			String reason) {
		var history = new CustomerHistory(List.of(), Optional.empty(), Optional.empty(), new AmountStats(count, mean, stddev), false);

		assertThat(rule.evaluate(event(amountMinor, "GROCERIES"), history))
			.isEqualTo(Optional.ofNullable(reason).map(r -> new RuleHit("AMOUNT_ANOMALY", 50, r)));
	}

	@Test
	void repeatPaymentNeverFires() {
		var history = new CustomerHistory(List.of(), Optional.empty(), Optional.empty(), new AmountStats(10, 45000, 5000), true);

		assertThat(rule.evaluate(event(99999, "GROCERIES"), history)).isEmpty();
	}

}
