package com.fraudengine.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

// RateLimitTest covers the 429 through the real filter chain.
class RateLimitFilterTest {

	static final long SECOND = 1_000_000_000L;

	@Test
	void burstIsAllowedThenTheRateRefillsIt() {
		var bucket = new RateLimitFilter.Bucket(3, 0);
		for (var i = 0; i < 3; i++) {
			assertThat(bucket.take(2, 3, 0)).isZero();
		}
		assertThat(bucket.take(2, 3, 0)).isEqualTo(1); // half a second to the next token, rounded up
		assertThat(bucket.take(2, 3, SECOND / 2)).isZero();
		assertThat(bucket.take(2, 3, SECOND / 2)).isPositive();
	}

	@Test
	void aLongQuietSpellRefillsOnlyUpToTheBurst() {
		var bucket = new RateLimitFilter.Bucket(0, 0);
		for (var i = 0; i < 3; i++) {
			assertThat(bucket.take(2, 3, 3600 * SECOND)).isZero();
		}
		assertThat(bucket.take(2, 3, 3600 * SECOND)).isPositive();
	}

	@Test
	void retryAfterIsTheWholeSecondsUntilATokenIsFree() {
		assertThat(new RateLimitFilter.Bucket(0, 0).take(0.1, 1, 0)).isEqualTo(10);
	}

	@Test
	void zeroRateOrBurstStopsStartup() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RateLimitFilter(0, 10, null))
			.withMessageContaining("FRAUD_RATE_LIMIT_PER_SECOND");
		assertThatIllegalArgumentException().isThrownBy(() -> new RateLimitFilter(10, 0, null))
			.withMessageContaining("FRAUD_RATE_LIMIT_BURST");
	}

}
