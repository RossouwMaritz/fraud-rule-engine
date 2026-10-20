package com.fraudengine.api;

import java.time.Instant;

import com.fraudengine.engine.Channel;
import com.fraudengine.engine.TransactionEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** {@code occurredAt} may be up to 5 minutes ahead, through the validator's temporal tolerance. */
public record TransactionRequest(@NotBlank @Size(max = 64) String transactionId,
		@NotBlank @Size(max = 64) String customerId, @Positive long amountMinor,
		@NotNull @Pattern(regexp = "[A-Z]{3}") String currency, @NotBlank @Size(max = 64) String merchantId,
		@NotBlank @Size(max = 64) String merchantCategory, @NotNull Channel channel,
		@NotNull @Pattern(regexp = "[A-Z]{2}") String merchantCountry, @NotNull @PastOrPresent Instant occurredAt) {

	TransactionEvent toEvent() {
		return new TransactionEvent(transactionId, customerId, amountMinor, currency, merchantId, merchantCategory,
				channel, merchantCountry, occurredAt);
	}

}
