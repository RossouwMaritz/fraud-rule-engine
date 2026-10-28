package com.fraudengine.api;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code clockSkew}: how far in the future an {@code occurredAt} may be, for clocks that differ between systems.
 * {@code defaultPageSize} and {@code maxPageSize}: the {@code size} of {@code GET /api/v1/flags}.
 */
@Validated
@ConfigurationProperties("fraud.api")
public record ApiProperties(@NotNull @DurationMin(seconds = 0) Duration clockSkew, @Positive int defaultPageSize,
		// At most the @BatchSize on TransactionEntity.hits, so a page still loads all its hits in one query.
		@Positive @Max(100) int maxPageSize) {

	@AssertTrue(message = "default-page-size must not be greater than max-page-size")
	@JsonIgnore
	public boolean isPageSizeOrdered() {
		return defaultPageSize <= maxPageSize;
	}

}
