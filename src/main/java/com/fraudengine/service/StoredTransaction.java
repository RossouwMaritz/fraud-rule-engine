package com.fraudengine.service;

import java.time.Instant;

import com.fraudengine.engine.Evaluation;
import com.fraudengine.engine.TransactionEvent;

/** A stored transaction and its evaluation, detached from JPA. */
public record StoredTransaction(TransactionEvent transaction, Instant receivedAt, Evaluation evaluation) {
}
