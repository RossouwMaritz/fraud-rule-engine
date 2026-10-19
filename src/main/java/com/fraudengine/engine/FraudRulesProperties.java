package com.fraudengine.engine;

import java.time.Duration;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fraud")
public record FraudRulesProperties(@NotNull @Valid Bands bands, @NotNull @Valid Rules rules) {

	/** Lowest total score for each decision. Anything below {@code reviewFrom} is CLEAN. */
	public record Bands(@Min(1) int reviewFrom, @Min(1) int highRiskFrom) {

		@AssertTrue(message = "high-risk-from must be greater than review-from")
		public boolean isOrdered() {
			return highRiskFrom > reviewFrom;
		}

	}

	public record Rules(@NotNull @Valid HighAmount highAmount, @NotNull @Valid AmountAnomaly amountAnomaly,
			@NotNull @Valid Velocity velocity, @NotNull @Valid CountryHop countryHop,
			@NotNull @Valid HighRiskCategory highRiskCategory) {
	}

	public record HighAmount(boolean enabled, @Min(0) int score, @Positive long thresholdMinor) {
	}

	public record AmountAnomaly(boolean enabled, @Min(0) int score, @Positive int minHistory,
			@NotNull Duration lookback, @Positive double stddevMultiplier) {
	}

	public record Velocity(boolean enabled, @Min(0) int score, @Positive int maxCount, @NotNull Duration window) {
	}

	public record CountryHop(boolean enabled, @Min(0) int score, @NotNull Duration window) {
	}

	public record HighRiskCategory(boolean enabled, @Min(0) int score, @NotEmpty Set<String> categories) {
	}

}
