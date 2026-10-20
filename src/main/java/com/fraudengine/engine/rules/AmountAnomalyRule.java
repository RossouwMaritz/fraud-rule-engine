package com.fraudengine.engine.rules;

import java.math.BigDecimal;
import java.util.Optional;

import com.fraudengine.engine.CustomerHistory;
import com.fraudengine.engine.FraudRule;
import com.fraudengine.engine.FraudRulesProperties;
import com.fraudengine.engine.RuleHit;
import com.fraudengine.engine.TransactionEvent;
import org.springframework.stereotype.Component;

/** Uses {@link CustomerHistory#amountStats()}, which the caller loads over the configured lookback. */
@Component
public class AmountAnomalyRule implements FraudRule {

	private final FraudRulesProperties.AmountAnomaly config;

	public AmountAnomalyRule(FraudRulesProperties properties) {
		this.config = properties.rules().amountAnomaly();
	}

	@Override
	public String id() {
		return "AMOUNT_ANOMALY";
	}

	@Override
	public boolean enabled() {
		return config.enabled();
	}

	@Override
	public Optional<RuleHit> evaluate(TransactionEvent txn, CustomerHistory history) {
		var stats = history.amountStats();
		// A stddev of 0 means every past amount was the same; nothing meaningful to compare against.
		// A repeat payment (rent, a debit order) is a known amount. Its own last payment inflates the stats too
		// little to clear it once the customer has 10+ small payments around it.
		if (stats.count() < config.minHistory() || stats.stddev() == 0 || history.repeatPayment()) {
			return Optional.empty();
		}
		double limit = stats.mean() + config.stddevMultiplier() * stats.stddev();
		if (txn.amountMinor() <= limit) {
			return Optional.empty();
		}
		var currency = txn.currency();
		return Optional.of(new RuleHit(id(), config.score(),
				"%s is over %s: the mean of the last %s (%s) plus %s standard deviations (%s), from %d transactions"
					.formatted(Format.money(txn.amountMinor(), currency), Format.money(Math.round(limit), currency),
							Format.duration(config.lookback()), Format.money(Math.round(stats.mean()), currency),
							BigDecimal.valueOf(config.stddevMultiplier()).stripTrailingZeros().toPlainString(),
							Format.money(Math.round(stats.stddev()), currency), stats.count())));
	}

}
