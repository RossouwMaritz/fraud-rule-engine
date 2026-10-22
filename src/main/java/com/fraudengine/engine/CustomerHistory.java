package com.fraudengine.engine;

import java.util.List;
import java.util.Optional;

/**
 * What the rules may know about a customer's other transactions. Loaded before evaluation, so rules never do I/O.
 * {@code recentTransactions} and the card-present neighbours are this card's only; the amount fields cover all the
 * customer's cards, because spending habits belong to the person.
 * Events can arrive late, so {@code recentTransactions} and {@code nextCardPresent} may hold stored transactions
 * that happened after this one. {@code repeatPayment} is true when the customer paid this merchant this exact amount
 * one billing cycle back: between the repeat min and max age.
 */
public record CustomerHistory(List<TransactionEvent> recentTransactions,
		Optional<TransactionEvent> previousCardPresent, Optional<TransactionEvent> nextCardPresent,
		AmountStats amountStats, boolean repeatPayment) {
}
