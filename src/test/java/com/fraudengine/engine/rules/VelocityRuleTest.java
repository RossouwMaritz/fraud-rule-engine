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
import org.junit.jupiter.api.Test;
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

	// Four on the same side of this one, plus this one, is 5; the fifth event on that side decides it.
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"-600 | 6 transactions in 10 minutes (limit 5)", // exactly a window before counts
			"-601 |", // a second further
			" 600 | 6 transactions in 10 minutes (limit 5)", // stored events after this one count too
			" 601 |"
	})
	void windowEdges(long offsetSeconds, String reason) {
		var others = new ArrayList<>(offsetSeconds < 0 ? minutesAgo(4) : minutesLater(4));
		others.add(event(Channel.ONLINE, "ZA", NOW.plusSeconds(offsetSeconds)));

		assertThat(rule.evaluate(event(Channel.ONLINE, "ZA", NOW), history(others))).isEqualTo(hit(reason));
	}

	// 7 charges 40 seconds apart. Time order flags the last 2. Reverse order (late events) must flag 2 as well.
	@Test
	void aBurstIsFlaggedTheSameInEitherArrivalOrder() {
		var burst = IntStream.range(0, 7).mapToObj(i -> event(Channel.ONLINE, "ZA", NOW.plusSeconds(40L * i))).toList();

		assertThat(hitsInArrivalOrder(burst)).isEqualTo(2);
		assertThat(hitsInArrivalOrder(burst.reversed())).isEqualTo(2);
	}

	// Equal times all fall in the window that starts at them, so 5 stored at this event's instant make 6.
	@Test
	void eventsAtTheSameInstantAllCount() {
		var sameTime = IntStream.range(0, 5).mapToObj(i -> event(Channel.ONLINE, "ZA", NOW)).toList();

		assertThat(rule.evaluate(event(Channel.ONLINE, "ZA", NOW), history(sameTime)))
			.isEqualTo(hit("6 transactions in 10 minutes (limit 5)"));
	}

	/** Each event sees the ones that arrived before it, as stored history does. */
	private long hitsInArrivalOrder(List<TransactionEvent> events) {
		return IntStream.range(0, events.size())
			.filter(i -> rule.evaluate(events.get(i), history(events.subList(0, i))).isPresent())
			.count();
	}

	/** One event a minute for the last {@code n} minutes. */
	private static List<TransactionEvent> minutesAgo(int n) {
		return IntStream.rangeClosed(1, n).mapToObj(i -> event(Channel.ONLINE, "ZA", NOW.minusSeconds(60L * i))).toList();
	}

	/** One event a minute for the next {@code n} minutes. */
	private static List<TransactionEvent> minutesLater(int n) {
		return IntStream.rangeClosed(1, n).mapToObj(i -> event(Channel.ONLINE, "ZA", NOW.plusSeconds(60L * i))).toList();
	}

	private static CustomerHistory history(List<TransactionEvent> recent) {
		return new CustomerHistory(recent, Optional.empty(), Optional.empty(), new AmountStats(0, 0, 0), false);
	}

	private static Optional<RuleHit> hit(String reason) {
		return Optional.ofNullable(reason).map(r -> new RuleHit("VELOCITY", 40, r));
	}

}
