package com.fraudengine.engine.rules;

import java.util.Optional;

import com.fraudengine.engine.CustomerHistory;
import com.fraudengine.engine.FraudRule;
import com.fraudengine.engine.FraudRulesProperties;
import com.fraudengine.engine.RuleHit;
import com.fraudengine.engine.TransactionEvent;
import org.springframework.stereotype.Component;

@Component
public class HighRiskCategoryRule implements FraudRule {

	private final FraudRulesProperties.HighRiskCategory config;

	public HighRiskCategoryRule(FraudRulesProperties properties) {
		this.config = properties.rules().highRiskCategory();
	}

	@Override
	public String id() {
		return "HIGH_RISK_CATEGORY";
	}

	@Override
	public boolean enabled() {
		return config.enabled();
	}

	// Exact match: categories arrive already normalised from upstream.
	@Override
	public Optional<RuleHit> evaluate(TransactionEvent txn, CustomerHistory history) {
		if (!config.categories().contains(txn.merchantCategory())) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(id(), config.score(),
				"Merchant category %s is on the high-risk list".formatted(txn.merchantCategory())));
	}

}
