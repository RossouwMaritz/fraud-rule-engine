package com.fraudengine.engine.rules;

import java.util.Optional;

import com.fraudengine.engine.RuleHit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.fraudengine.engine.rules.TestEvents.NO_HISTORY;
import static com.fraudengine.engine.rules.TestEvents.PROPERTIES;
import static com.fraudengine.engine.rules.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;

class HighAmountRuleTest {

	private final HighAmountRule rule = new HighAmountRule(PROPERTIES);

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"4999999 |", // just under
			"5000000 |", // exactly at: the limit itself is allowed
			"5000001 | ZAR 50,000.01 is over the ZAR 50,000.00 limit", // just over
			"6000000 | ZAR 60,000.00 is over the ZAR 50,000.00 limit" })
	void firesOnlyOverTheThreshold(long amountMinor, String reason) {
		// History plays no part, so every case runs with none.
		assertThat(rule.evaluate(event(amountMinor, "GROCERIES"), NO_HISTORY))
			.isEqualTo(Optional.ofNullable(reason).map(r -> new RuleHit("HIGH_AMOUNT", 40, r)));
	}

}
