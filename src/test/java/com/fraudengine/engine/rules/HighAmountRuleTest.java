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
			"ZAR | 4999999 |", // just under
			"ZAR | 5000000 |", // exactly at: the limit itself is allowed
			"ZAR | 5000001 | ZAR 50,000.01 is over the ZAR 50,000.00 limit", // just over
			"ZAR | 6000000 | ZAR 60,000.00 is over the ZAR 50,000.00 limit",
			// Each currency has its own limit: $3,000.00, not 5,000,000 cents.
			"USD | 299999 |",
			"USD | 300000 |",
			"USD | 300001 | USD 3,000.01 is over the USD 3,000.00 limit",
			// No limit configured, so even a small amount fires.
			"JPY | 100 | No HIGH_AMOUNT limit configured for JPY" })
	void firesOnlyOverTheCurrencysThreshold(String currency, long amountMinor, String reason) {
		// History plays no part, so every case runs with none.
		assertThat(rule.evaluate(event(amountMinor, currency, "GROCERIES"), NO_HISTORY))
			.isEqualTo(Optional.ofNullable(reason).map(r -> new RuleHit("HIGH_AMOUNT", 40, r)));
	}

}
