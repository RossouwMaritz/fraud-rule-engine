package com.fraudengine.engine.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fraudengine.engine.AmountStats;
import com.fraudengine.engine.Channel;
import com.fraudengine.engine.CustomerHistory;
import com.fraudengine.engine.FraudRulesProperties;
import com.fraudengine.engine.TransactionEvent;

/** The application.yml defaults, and a terse way to build events for rule tests. */
final class TestEvents {

	static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

	static final FraudRulesProperties PROPERTIES = new FraudRulesProperties(new FraudRulesProperties.Bands(40, 80),
			new FraudRulesProperties.Rules(new FraudRulesProperties.HighAmount(true, 40, 5_000_000),
					new FraudRulesProperties.AmountAnomaly(true, 50, 10, Duration.ofDays(30), 3, Duration.ofDays(25),
							Duration.ofDays(35)),
					new FraudRulesProperties.Velocity(true, 40, 5, Duration.ofMinutes(10)),
					new FraudRulesProperties.CountryHop(true, 80, Duration.ofHours(2)),
					new FraudRulesProperties.HighRiskCategory(true, 20, Set.of("GAMBLING", "CRYPTO", "MONEY_TRANSFER"))),
			Duration.ofDays(45));

	static final CustomerHistory NO_HISTORY = new CustomerHistory(List.of(), Optional.empty(), Optional.empty(),
			new AmountStats(0, 0, 0), false);

	private TestEvents() {
	}

	static TransactionEvent event(long amountMinor, String category) {
		return new TransactionEvent("txn_1", "cus_1", amountMinor, "ZAR", "mer_1", category, Channel.CARD_PRESENT,
				"ZA", NOW);
	}

	static TransactionEvent event(Channel channel, String country, Instant occurredAt) {
		return new TransactionEvent("txn_" + occurredAt.getEpochSecond(), "cus_1", 10_000, "ZAR", "mer_1",
				"GROCERIES", channel, country, occurredAt);
	}

}
