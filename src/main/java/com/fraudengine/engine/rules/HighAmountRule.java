package com.fraudengine.engine.rules;

import java.util.Optional;

import com.fraudengine.engine.CustomerHistory;
import com.fraudengine.engine.FraudRule;
import com.fraudengine.engine.FraudRulesProperties;
import com.fraudengine.engine.RuleHit;
import com.fraudengine.engine.TransactionEvent;
import org.springframework.stereotype.Component;

@Component
public class HighAmountRule implements FraudRule {

	private final FraudRulesProperties.HighAmount config;

	public HighAmountRule(FraudRulesProperties properties) {
		this.config = properties.rules().highAmount();
	}

	@Override
	public String id() {
		return "HIGH_AMOUNT";
	}

	@Override
	public boolean enabled() {
		return config.enabled();
	}

	@Override
	public Optional<RuleHit> evaluate(TransactionEvent txn, CustomerHistory history) {
		var limit = config.thresholdsMinor().get(txn.currency());
		if (limit == null) {
			// We only flag, never block, so an unlisted currency fails safe by going to review.
			return Optional.of(new RuleHit(id(), config.score(), "No HIGH_AMOUNT limit configured for " + txn.currency()));
		}
		if (txn.amountMinor() <= limit) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(id(), config.score(),
				"%s is over the %s limit".formatted(Format.money(txn.amountMinor(), txn.currency()),
						Format.money(limit, txn.currency()))));
	}

}
