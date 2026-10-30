package com.fraudengine.api;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

// ApiKeySecurityTest covers the 413 and 411 responses through the real filter chain.
class RequestSizeFilterTest {

	@Test
	void zeroOrNegativeLimitStopsStartup() {
		for (var limit : new String[] { "0", "-1KB" }) {
			assertThatIllegalArgumentException().isThrownBy(() -> new RequestSizeFilter(DataSize.parse(limit), null))
				.withMessageContaining("FRAUD_MAX_REQUEST_BODY");
		}
	}

}
