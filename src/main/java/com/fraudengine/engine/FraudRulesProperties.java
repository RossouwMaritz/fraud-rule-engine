package com.fraudengine.engine;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code maxEventAge}: an event whose {@code occurredAt} is older than this is rejected, not scored. */
@Validated
@ConfigurationProperties("fraud")
public record FraudRulesProperties(@NotNull @Valid Bands bands, @NotNull @Valid Rules rules,
		@NotNull @DurationMin(days = 1) Duration maxEventAge) {

	/** Lowest total score for each decision. Anything below {@code reviewFrom} is CLEAN. */
	public record Bands(@Min(1) int reviewFrom, @Min(1) int highRiskFrom) {

		@AssertTrue(message = "high-risk-from must be greater than review-from")
		@JsonIgnore // a validation check, not config; keeps it out of GET /api/v1/rules
		public boolean isOrdered() {
			return highRiskFrom > reviewFrom;
		}

	}

	public record Rules(@NotNull @Valid HighAmount highAmount, @NotNull @Valid AmountAnomaly amountAnomaly,
			@NotNull @Valid Velocity velocity, @NotNull @Valid CountryHop countryHop,
			@NotNull @Valid HighRiskCategory highRiskCategory) {
	}

	/** {@code thresholdsMinor}: the limit per currency code, in that currency's minor units. */
	public record HighAmount(boolean enabled, @Min(0) int score, @NotNull Map<String, @Positive Long> thresholdsMinor) {

		// Env var map keys bind lowercase (FRAUD_RULES_HIGHAMOUNT_THRESHOLDSMINOR_JPY becomes "jpy"), and codes are upper.
		public HighAmount {
			if (thresholdsMinor != null) {
				thresholdsMinor = thresholdsMinor.entrySet()
					.stream()
					.collect(Collectors.toMap(e -> e.getKey().toUpperCase(Locale.ROOT), Map.Entry::getValue, (a, b) -> a));
			}
		}

	}

	public record AmountAnomaly(boolean enabled, @Min(0) int score, @Positive int minHistory,
			@NotNull @DurationMin(seconds = 1) Duration lookback, @Positive double stddevMultiplier,
			@NotNull @DurationMin(seconds = 1) Duration repeatMinAge,
			@NotNull @DurationMin(seconds = 1) Duration repeatMaxAge) {

		@AssertTrue(message = "repeat-max-age must be greater than repeat-min-age")
		@JsonIgnore // a validation check, not config; keeps it out of GET /api/v1/rules
		public boolean isRepeatWindowOrdered() {
			return repeatMinAge == null || repeatMaxAge == null || repeatMaxAge.compareTo(repeatMinAge) > 0;
		}

	}

	public record Velocity(boolean enabled, @Min(0) int score, @Positive int maxCount,
			@NotNull @DurationMin(seconds = 1) Duration window) {
	}

	public record CountryHop(boolean enabled, @Min(0) int score, @NotNull @DurationMin(seconds = 1) Duration window) {
	}

	public record HighRiskCategory(boolean enabled, @Min(0) int score, @NotEmpty Set<String> categories) {
	}

}
