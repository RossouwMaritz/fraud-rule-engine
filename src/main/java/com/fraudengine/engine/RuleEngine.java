package com.fraudengine.engine;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RuleEngine {

	private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

	private final List<FraudRule> rules;

	private final FraudRulesProperties.Bands bands;

	public RuleEngine(List<FraudRule> rules, FraudRulesProperties properties) {
		this.rules = List.copyOf(rules);
		this.bands = properties.bands();
	}

	public Evaluation evaluate(TransactionEvent txn, CustomerHistory history) {
		var hits = new ArrayList<RuleHit>();
		var errored = new ArrayList<String>();
		for (var rule : rules) {
			if (!rule.enabled()) {
				continue;
			}
			// One broken rule must not stop the others: record the gap and move on.
			try {
				rule.evaluate(txn, history).ifPresent(hits::add);
			}
			catch (RuntimeException ex) {
				log.error("Rule {} failed for transaction {}", rule.id(), txn.transactionId(), ex);
				errored.add(rule.id());
			}
		}
		int total = hits.stream().mapToInt(RuleHit::score).sum();
		return new Evaluation(total, decide(total), List.copyOf(hits), List.copyOf(errored));
	}

	private Decision decide(int total) {
		if (total >= bands.highRiskFrom()) {
			return Decision.HIGH_RISK;
		}
		if (total >= bands.reviewFrom()) {
			return Decision.REVIEW;
		}
		return Decision.CLEAN;
	}

}
