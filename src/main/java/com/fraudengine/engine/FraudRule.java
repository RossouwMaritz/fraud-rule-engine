package com.fraudengine.engine;

import java.util.Optional;

/** One fraud check. Implementations must be pure: everything they need is in the arguments. */
public interface FraudRule {

	String id();

	/** From the rule's own config, so the engine needs no per-rule lookup. */
	boolean enabled();

	Optional<RuleHit> evaluate(TransactionEvent txn, CustomerHistory history);

}
