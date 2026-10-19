package com.fraudengine.engine.rules;

import java.util.Optional;

import com.fraudengine.engine.RuleHit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.fraudengine.engine.rules.TestEvents.NO_HISTORY;
import static com.fraudengine.engine.rules.TestEvents.PROPERTIES;
import static com.fraudengine.engine.rules.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;

class HighRiskCategoryRuleTest {

	private final HighRiskCategoryRule rule = new HighRiskCategoryRule(PROPERTIES);

	// No numeric threshold here, so the cases are each listed category and some that aren't listed.
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"GAMBLING       | Merchant category GAMBLING is on the high-risk list",
			"CRYPTO         | Merchant category CRYPTO is on the high-risk list",
			"MONEY_TRANSFER | Merchant category MONEY_TRANSFER is on the high-risk list",
			"GROCERIES      |", //
			"GAMBLING_ADS   |" // similar name is not a match
	})
	void firesOnlyForListedCategories(String category, String reason) {
		assertThat(rule.evaluate(event(10_000, category), NO_HISTORY))
			.isEqualTo(Optional.ofNullable(reason).map(r -> new RuleHit("HIGH_RISK_CATEGORY", 20, r)));
	}

}
