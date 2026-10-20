package com.fraudengine.service;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import com.fraudengine.engine.CustomerHistory;
import com.fraudengine.engine.FraudRulesProperties;
import com.fraudengine.engine.RuleEngine;
import com.fraudengine.engine.RuleHit;
import com.fraudengine.engine.TransactionEvent;
import com.fraudengine.persistence.TransactionEntity;
import com.fraudengine.persistence.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {

	private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

	private final TransactionRepository repository;

	private final RuleEngine engine;

	private final MeterRegistry meters;

	private final Duration recentWindow;

	private final Duration amountLookback;

	public EvaluationService(TransactionRepository repository, RuleEngine engine, MeterRegistry meters,
			FraudRulesProperties properties) {
		this.repository = repository;
		this.engine = engine;
		this.meters = meters;
		// Only VELOCITY reads recent transactions, so load exactly its window.
		this.recentWindow = properties.rules().velocity().window();
		this.amountLookback = properties.rules().amountAnomaly().lookback();
	}

	@Transactional
	public EvaluationResult evaluate(TransactionEvent txn) {
		var sample = Timer.start(meters);
		try {
			// Serialise each customer's events so history rules see the ones before them.
			repository.lockCustomer(txn.customerId());

			var stored = repository.findById(txn.transactionId());
			if (stored.isPresent()) {
				// Postgres keeps microseconds and the driver rounds half up, so round the same way before comparing.
				var sameEvent = new TransactionEvent(txn.transactionId(), txn.customerId(), txn.amountMinor(),
						txn.currency(), txn.merchantId(), txn.merchantCategory(), txn.channel(), txn.merchantCountry(),
						txn.occurredAt().plusNanos(500).truncatedTo(ChronoUnit.MICROS));
				if (!stored.get().toEvent().equals(sameEvent)) {
					throw new TransactionConflictException(txn.transactionId());
				}
				var result = new EvaluationResult(txn.transactionId(), stored.get().toEvaluation(), true);
				logResult(txn, result);
				return result;
			}

			// Load history before saving, so it holds only earlier transactions.
			var at = txn.occurredAt();
			var history = new CustomerHistory(
					repository.findRecent(txn.customerId(), at.minus(recentWindow), at)
						.stream()
						.map(TransactionEntity::toEvent)
						.toList(),
					repository.findPreviousCardPresent(txn.customerId(), at).map(TransactionEntity::toEvent),
					repository.amountStats(txn.customerId(), at.minus(amountLookback), at));
			var evaluation = engine.evaluate(txn, history);
			// Flush now so a failed insert stops us before the metrics and the log claim success.
			repository.saveAndFlush(TransactionEntity.of(txn, evaluation));

			meters.counter("fraud.evaluations", "decision", evaluation.decision().name()).increment();
			evaluation.hits().forEach(hit -> meters.counter("fraud.rule.hits", "rule", hit.ruleId()).increment());
			var result = new EvaluationResult(txn.transactionId(), evaluation, false);
			logResult(txn, result);
			return result;
		}
		finally {
			sample.stop(meters.timer("fraud.evaluation.duration"));
		}
	}

	// No amounts here: amounts next to customer IDs must not reach INFO logs.
	private static void logResult(TransactionEvent txn, EvaluationResult result) {
		var evaluation = result.evaluation();
		log.info("Evaluated transactionId={} customerId={} decision={} totalScore={} rules={} rulesErrored={} replay={}",
				txn.transactionId(), txn.customerId(), evaluation.decision(), evaluation.totalScore(),
				evaluation.hits().stream().map(RuleHit::ruleId).toList(), evaluation.rulesErrored(), result.replay());
	}

}
