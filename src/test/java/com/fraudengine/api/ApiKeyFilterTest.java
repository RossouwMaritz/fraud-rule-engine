package com.fraudengine.api;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApiKeyFilterTest {

	private static final String OLD_KEY = "old-key-0123456789abcdefghijklmnop";

	private static final String NEW_KEY = "new-key-0123456789abcdefghijklmnop";

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void duringRotationTheOldAndNewKeyBothWork() throws Exception {
		var filter = new ApiKeyFilter(List.of(OLD_KEY, NEW_KEY));
		assertThat(authenticates(filter, OLD_KEY)).isTrue();
		assertThat(authenticates(filter, NEW_KEY)).isTrue();
		assertThat(authenticates(filter, NEW_KEY + "x")).isFalse();
		assertThat(authenticates(filter, null)).isFalse();
	}

	@Test
	void aShortOrMissingKeyStopsStartup() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ApiKeyFilter(List.of(OLD_KEY, "x".repeat(31))))
			.withMessageContaining("at least 32 characters");
		assertThatIllegalArgumentException().isThrownBy(() -> new ApiKeyFilter(List.of()))
			.withMessageContaining("FRAUD_API_KEYS");
	}

	private static boolean authenticates(ApiKeyFilter filter, String key) throws Exception {
		SecurityContextHolder.clearContext();
		var request = new MockHttpServletRequest();
		if (key != null) {
			request.addHeader(ApiKeyFilter.HEADER, key);
		}
		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
		return SecurityContextHolder.getContext().getAuthentication() != null;
	}

}
