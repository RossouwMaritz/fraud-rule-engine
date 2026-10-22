package com.fraudengine.engine;

import java.time.Instant;

public record TransactionEvent(String transactionId, String customerId, String cardId, long amountMinor,
		String currency, String merchantId, String merchantCategory, Channel channel, String merchantCountry,
		Instant occurredAt) {
}
