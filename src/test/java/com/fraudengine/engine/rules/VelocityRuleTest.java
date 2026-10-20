package com.fraudengine.engine.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.fraudengine.engine.AmountStats;
import com.fraudengine.engine.Channel;
import com.fraudengine.engine.CustomerHistory;
import com.fraudengine.engine.RuleHit;
import com.fraudengine.engine.TransactionEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.fraudengine.engine.rules.TestEvents.NOW;
import static com.fraudengine.engine.rules.TestEvents.PROPERTIES;
import static com.fraudengine.engine.rules.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;

class VelocityRuleTest {

	private final VelocityRule rule = new VelocityRule(PROPERTIES);

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"0 |", // empty history
			"3 |", // 4 including this one: just under
			"4 |", // 5: exactly at the limit
			"5 | 6 transactions in 10 minutes (limit 5)" // just over
	})
	void firesOnlyOverTheLimit(int earlierInWindow, String reason) {
		assertThat(rule.evaluate(event(Channel.ONLINE, "ZA", NOW), history(minutesAgo(earlierInWindow))))
			.isEqualTo(hit(reason));
	}

	// Four earlier in the window plus this one is 5; the fifth earlier event decides it.
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"-600 | 6 transactions in 10 minutes (limit 5)", // exactly at the window start counts
			"-601 |", // a second before the window
			"   1 |" // after this transaction: not "up to" it
	})
	void windowEdges(long offsetSeconds, String reason) {
		var earlier = new ArrayList<>(minutesAgo(4));
		earlier.add(event(Channel.ONLINE, "ZA", NOW.plusSeconds(offsetSeconds)));

		assertThat(rule.evaluate(event(Channel.ONLINE, "ZA", NOW), history(earlier))).isEqualTo(hit(reason));
	}

	/** One event a minute for the last {@code n} minutes. */
	private static List<TransactionEvent> minutesAgo(int n) {
		return IntStream.rangeClosed(1, n).mapToObj(i -> event(Channel.ONLINE, "ZA", NOW.minusSeconds(60L * i))).toList();
	}

	private static CustomerHistory history(List<TransactionEvent> recent) {
		return new CustomerHistory(recent, Optional.empty(), new AmountStats(0, 0, 0), false);
	}

	private static Optional<RuleHit> hit(String reason) {
		return Optional.ofNullable(reason).map(r -> new RuleHit("VELOCITY", 40, r));
	}

}
