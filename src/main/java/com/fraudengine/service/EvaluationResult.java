package com.fraudengine.service;

import com.fraudengine.engine.Evaluation;

/** A stored evaluation. {@code replay} is true when the transaction ID was already stored and nothing was re-run. */
public record EvaluationResult(String transactionId, Evaluation evaluation, boolean replay) {
}
