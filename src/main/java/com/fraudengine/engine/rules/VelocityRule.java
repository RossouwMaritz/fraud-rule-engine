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
			.sorted()
			.toList();
		// Every window that holds this event starts at an event in [at - window, at]. With the times sorted, the
		// window's end index only moves forward, so one pass counts them all.
		long count = 0;
		for (int start = 0, end = 0; start < times.size(); start++) {
			var from = times.get(start);
			if (from.isBefore(at.minus(window)) || from.isAfter(at)) {
				continue;
			}
			end = Math.max(end, start);
			while (end < times.size() && !times.get(end).isAfter(from.plus(window))) {
				end++;
			}
			count = Math.max(count, end - start);
		}
		if (count <= config.maxCount()) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(id(), config.score(), "%d transactions in %s (limit %d)".formatted(count,
				Format.duration(config.window()), config.maxCount())));
	}

}
