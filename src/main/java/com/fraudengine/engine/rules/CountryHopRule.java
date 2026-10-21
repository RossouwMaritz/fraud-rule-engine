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
		// A late event can be the first half of a hop whose second half is already stored.
		return history.previousCardPresent()
			.flatMap(prev -> hop(txn, prev, Duration.between(prev.occurredAt(), txn.occurredAt()), "after"))
			.or(() -> history.nextCardPresent()
				.flatMap(next -> hop(txn, next, Duration.between(txn.occurredAt(), next.occurredAt()), "before")));
	}

	private Optional<RuleHit> hop(TransactionEvent txn, TransactionEvent other, Duration gap, String direction) {
		if (other.merchantCountry().equals(txn.merchantCountry()) || gap.compareTo(config.window()) >= 0) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(id(), config.score(),
				"Card-present in %s %s %s card-present in %s (window %s)".formatted(txn.merchantCountry(),
						Format.duration(gap), direction, other.merchantCountry(), Format.duration(config.window()))));
	}

}
