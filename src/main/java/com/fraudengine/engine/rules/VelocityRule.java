package com.fraudengine.engine.rules;

import java.util.Optional;
import java.util.stream.Stream;

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
		var at = txn.occurredAt();
		var window = config.window();
		// History holds only stored transactions, so add this one. Events can arrive late, so count the busiest
		// window that contains this event, which may start before it or end after it. Both window ends count.
		var times = Stream
			.concat(history.recentTransactions().stream().map(TransactionEvent::occurredAt), Stream.of(at))
			.toList();
		// shortcut: O(n²) over the events within one window of this one; fine for tens, sort and slide two pointers
		// if one card can have thousands.
		long count = times.stream()
			.filter(start -> !start.isBefore(at.minus(window)) && !start.isAfter(at))
			.mapToLong(start -> times.stream().filter(t -> !t.isBefore(start) && !t.isAfter(start.plus(window))).count())
			.max()
			.orElseThrow();
		if (count <= config.maxCount()) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(id(), config.score(), "%d transactions in %s (limit %d)".formatted(count,
				Format.duration(config.window()), config.maxCount())));
	}

}
