package com.fraudengine.engine.rules;

import java.time.Duration;
import java.util.Optional;

import com.fraudengine.engine.Channel;
import com.fraudengine.engine.CustomerHistory;
import com.fraudengine.engine.FraudRule;
import com.fraudengine.engine.FraudRulesProperties;
import com.fraudengine.engine.RuleHit;
import com.fraudengine.engine.TransactionEvent;
import org.springframework.stereotype.Component;

/** Card-present only: an online merchant's country says nothing about where the card is. */
@Component
public class CountryHopRule implements FraudRule {

	private final FraudRulesProperties.CountryHop config;

	public CountryHopRule(FraudRulesProperties properties) {
		this.config = properties.rules().countryHop();
	}

	@Override
	public String id() {
		return "COUNTRY_HOP";
	}

	@Override
	public boolean enabled() {
		return config.enabled();
	}

	@Override
	public Optional<RuleHit> evaluate(TransactionEvent txn, CustomerHistory history) {
		if (txn.channel() != Channel.CARD_PRESENT) {
			return Optional.empty();
		}
		return history.previousCardPresent()
			.filter(prev -> !prev.merchantCountry().equals(txn.merchantCountry()))
			.flatMap(prev -> {
				var gap = Duration.between(prev.occurredAt(), txn.occurredAt());
				if (gap.compareTo(config.window()) >= 0) {
					return Optional.empty();
				}
				return Optional.of(new RuleHit(id(), config.score(),
						"Card-present in %s %s after card-present in %s (window %s)".formatted(
								txn.merchantCountry(), Format.duration(gap), prev.merchantCountry(),
								Format.duration(config.window()))));
			});
	}

}
