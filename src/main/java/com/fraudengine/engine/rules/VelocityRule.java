package com.fraudengine.engine.rules;

import java.util.Optional;

import com.fraudengine.engine.CustomerHistory;
import com.fraudengine.engine.FraudRule;
import com.fraudengine.engine.FraudRulesProperties;
import com.fraudengine.engine.RuleHit;
import com.fraudengine.engine.TransactionEvent;
import org.springframework.stereotype.Component;

@Component
public class VelocityRule implements FraudRule {

	private final FraudRulesProperties.Velocity config;

	public VelocityRule(FraudRulesProperties properties) {
		this.config = properties.rules().velocity();
	}

	@Override
	public String id() {
		return "VELOCITY";
	}

	@Override
	public boolean enabled() {
		return config.enabled();
	}

	@Override
	public Optional<RuleHit> evaluate(TransactionEvent txn, CustomerHistory history) {
		var to = txn.occurredAt();
		var from = to.minus(config.window());
		// History holds only stored transactions, so add 1 for this one. Both window ends count.
		long count = 1 + history.recentTransactions()
			.stream()
			.map(TransactionEvent::occurredAt)
			.filter(at -> !at.isBefore(from) && !at.isAfter(to))
			.count();
		if (count <= config.maxCount()) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(id(), config.score(), "%d transactions in %s (limit %d)".formatted(count,
				Format.duration(config.window()), config.maxCount())));
	}

}
