package com.fraudengine.engine;

import java.util.List;
import java.util.Optional;

/**
 * What the rules may know about a customer's past. Loaded before evaluation, so rules never do I/O.
 * {@code repeatPayment} is true when the customer paid this merchant this exact amount within the repeat lookback.
 */
public record CustomerHistory(List<TransactionEvent> recentTransactions,
		Optional<TransactionEvent> previousCardPresent, AmountStats amountStats, boolean repeatPayment) {
}
