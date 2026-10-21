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
			"CARD_PRESENT | ZA |   0 | Card-present in GB 0 minutes after card-present in ZA (window 2 hours)", // same moment
			"CARD_PRESENT | GB |  10 |", // same country
			"ONLINE       | ZA |  10 |", // online never fires
			"CARD_PRESENT |    |   0 |" // no previous card-present transaction
	})
	void firesOnlyForACardPresentCountryChangeInsideTheWindow(Channel channel, String previousCountry,
			long gapMinutes, String reason) {
		var previous = Optional.ofNullable(previousCountry)
			.map(country -> event(Channel.CARD_PRESENT, country, NOW.minusSeconds(60 * gapMinutes)));
		var history = new CustomerHistory(List.of(), previous, Optional.empty(), new AmountStats(0, 0, 0), false);

		assertThat(rule.evaluate(event(channel, "GB", NOW), history))
			.isEqualTo(Optional.ofNullable(reason).map(r -> new RuleHit("COUNTRY_HOP", 80, r)));
	}

	// A late event: this GB transaction at NOW arrives after a stored card-present one gapMinutes later.
	// The previous one, when there is one, is in GB 10 minutes earlier, so it never fires on its own.
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"ZA | 119 |      | Card-present in GB 119 minutes before card-present in ZA (window 2 hours)",
			"ZA | 120 |      |", // exactly the window
			"GB |  30 |      |", // same country
			"ZA |  30 | true | Card-present in GB 30 minutes before card-present in ZA (window 2 hours)"
	})
	void firesForAStoredCardPresentTransactionAfterThisOne(String nextCountry, long gapMinutes,
			Boolean withSameCountryPrevious, String reason) {
		var previous = Optional.ofNullable(withSameCountryPrevious)
			.map(x -> event(Channel.CARD_PRESENT, "GB", NOW.minusSeconds(600)));
		var next = Optional.of(event(Channel.CARD_PRESENT, nextCountry, NOW.plusSeconds(60 * gapMinutes)));
		var history = new CustomerHistory(List.of(), previous, next, new AmountStats(0, 0, 0), false);

		assertThat(rule.evaluate(event(Channel.CARD_PRESENT, "GB", NOW), history))
			.isEqualTo(Optional.ofNullable(reason).map(r -> new RuleHit("COUNTRY_HOP", 80, r)));
	}

}
