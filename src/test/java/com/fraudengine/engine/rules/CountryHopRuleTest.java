package com.fraudengine.engine.rules;

import java.util.List;
import java.util.Optional;

import com.fraudengine.engine.AmountStats;
import com.fraudengine.engine.Channel;
import com.fraudengine.engine.CustomerHistory;
import com.fraudengine.engine.RuleHit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.fraudengine.engine.rules.TestEvents.NOW;
import static com.fraudengine.engine.rules.TestEvents.PROPERTIES;
import static com.fraudengine.engine.rules.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;

class CountryHopRuleTest {

	private final CountryHopRule rule = new CountryHopRule(PROPERTIES);

	// This transaction is always in GB at NOW. The previous card-present one is gapMinutes earlier.
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"CARD_PRESENT | ZA | 119 | Card-present in GB 119 minutes after card-present in ZA (window 2 hours)",
			"CARD_PRESENT | ZA | 120 |", // exactly the window
			"CARD_PRESENT | ZA | 121 |", // just over the window
			"CARD_PRESENT | ZA |  40 | Card-present in GB 40 minutes after card-present in ZA (window 2 hours)",
			"CARD_PRESENT | GB |  10 |", // same country
			"ONLINE       | ZA |  10 |", // online never fires
			"CARD_PRESENT |    |   0 |" // no previous card-present transaction
	})
	void firesOnlyForACardPresentCountryChangeInsideTheWindow(Channel channel, String previousCountry,
			long gapMinutes, String reason) {
		var previous = Optional.ofNullable(previousCountry)
			.map(country -> event(Channel.CARD_PRESENT, country, NOW.minusSeconds(60 * gapMinutes)));
		var history = new CustomerHistory(List.of(), previous, new AmountStats(0, 0, 0));

		assertThat(rule.evaluate(event(channel, "GB", NOW), history))
			.isEqualTo(Optional.ofNullable(reason).map(r -> new RuleHit("COUNTRY_HOP", 80, r)));
	}

}
