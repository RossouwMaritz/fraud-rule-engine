package com.fraudengine.service;

import java.time.Instant;

import com.fraudengine.engine.Evaluation;
import com.fraudengine.engine.TransactionEvent;

/** A stored transaction, its evaluation and the rule config version that produced it, detached from JPA. */
public record StoredTransaction(TransactionEvent transaction, Instant receivedAt, Evaluation evaluation,
		String configVersion) {
}
