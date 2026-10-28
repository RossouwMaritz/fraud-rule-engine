package com.fraudengine.api;

import java.time.Duration;

import com.fraudengine.service.TransactionQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.web.ErrorResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// FraudApiControllerTest runs with the default max of 100, so it can't tell a lower configured max is enforced.
class FraudApiControllerUnitTest {

	private final TransactionQueryService queries = mock(TransactionQueryService.class);

	private final FraudApiController controller = new FraudApiController(null, queries, null, null,
			new ApiProperties(Duration.ofMinutes(5), 20, 50));

	@Test
	void sizeOverTheConfiguredMaxIs400() {
		assertThatThrownBy(() -> controller.flags(null, null, null, null, null, 0, 51))
			.isInstanceOfSatisfying(ErrorResponseException.class,
					e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
	}

	@Test
	void sizeAtTheConfiguredMaxIsAllowed() {
		when(queries.flags(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());
		assertThat(controller.flags(null, null, null, null, null, 0, 50).getContent()).isEmpty();
	}

}
