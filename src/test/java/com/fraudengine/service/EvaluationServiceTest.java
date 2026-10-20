package com.fraudengine.service;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import com.fraudengine.TestcontainersConfiguration;
import com.fraudengine.engine.Channel;
import com.fraudengine.engine.Decision;
import com.fraudengine.engine.RuleHit;
import com.fraudengine.engine.TransactionEvent;
import com.fraudengine.persistence.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EvaluationServiceTest {

	private static final Instant T0 = Instant.parse("2026-10-19T10:00:00Z");

	@Autowired
	EvaluationService service;

	@Autowired
	TransactionRepository repository;

	@Test
	void replayReturnsTheStoredResultWithoutASecondRow() {
		var txn = event("txn_replay", "cus_replay", 6_000_000, "GAMBLING", T0);

		var first = service.evaluate(txn);
		var second = service.evaluate(txn);

		assertThat(first.replay()).isFalse();
		assertThat(first.evaluation().decision()).isEqualTo(Decision.REVIEW);
		assertThat(first.evaluation().hits()).extracting(RuleHit::ruleId)
			.containsExactlyInAnyOrder("HIGH_AMOUNT", "HIGH_RISK_CATEGORY");
		assertThat(second.replay()).isTrue();
		assertThat(second.evaluation()).isEqualTo(first.evaluation());
		assertThat(repository.findRecent("cus_replay", T0, T0)).hasSize(1);
	}

	@Test
	void parallelEventsForOneCustomerAreSerialised() throws Exception {
		// Same occurredAt for all, so each event's velocity count is its position in the order they were processed.
		var start = new CountDownLatch(1);
		var tasks = IntStream.rangeClosed(1, 10).<Callable<EvaluationResult>>mapToObj(i -> () -> {
			start.await();
			return service.evaluate(event("txn_par_" + i, "cus_par", 10_000, "GROCERIES", T0));
		}).toList();

		try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
			var futures = tasks.stream().map(pool::submit).toList();
			start.countDown();
			var reasons = futures.stream()
				.map(EvaluationServiceTest::join)
				.flatMap(result -> result.evaluation().hits().stream())
				.filter(hit -> hit.ruleId().equals("VELOCITY"))
				.map(RuleHit::reason)
				.toList();

			// Without the lock, two events could see the same count and these would not all be distinct.
			assertThat(reasons).containsExactlyInAnyOrder("6 transactions in 10 minutes (limit 5)",
					"7 transactions in 10 minutes (limit 5)", "8 transactions in 10 minutes (limit 5)",
					"9 transactions in 10 minutes (limit 5)", "10 transactions in 10 minutes (limit 5)");
		}
		assertThat(repository.findRecent("cus_par", T0, T0)).hasSize(10);
	}

	private static EvaluationResult join(Future<EvaluationResult> future) {
		try {
			return future.get();
		}
		catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	private static TransactionEvent event(String id, String customerId, long amountMinor, String category,
			Instant occurredAt) {
		return new TransactionEvent(id, customerId, amountMinor, "ZAR", "mer_1", category, Channel.CARD_PRESENT, "ZA",
				occurredAt);
	}

}
