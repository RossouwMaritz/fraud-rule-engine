package com.fraudengine.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import com.fraudengine.engine.Decision;
import com.fraudengine.persistence.RuleHitEntity;
import com.fraudengine.persistence.TransactionEntity;
import com.fraudengine.persistence.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads stored transactions. Maps inside the transaction because open-in-view is off and hits load lazily. */
@Service
@Transactional(readOnly = true)
public class TransactionQueryService {

	private final TransactionRepository repository;

	public TransactionQueryService(TransactionRepository repository) {
		this.repository = repository;
	}

	public Optional<StoredTransaction> find(String transactionId) {
		return repository.findById(transactionId).map(TransactionQueryService::toStored);
	}

	/** Non-CLEAN transactions, newest first. Every filter is optional; {@code from} is inclusive, {@code to} exclusive. */
	public Page<StoredTransaction> flags(String customerId, Decision decision, String ruleId, Instant from, Instant to,
			int page, int size) {
		var specs = new ArrayList<Specification<TransactionEntity>>();
		specs.add((t, q, cb) -> cb.notEqual(t.get("decision"), Decision.CLEAN));
		if (customerId != null) {
			specs.add((t, q, cb) -> cb.equal(t.get("customerId"), customerId));
		}
		if (decision != null) {
			specs.add((t, q, cb) -> cb.equal(t.get("decision"), decision));
		}
		if (ruleId != null) {
			specs.add((t, q, cb) -> {
				var hit = q.subquery(Integer.class);
				var h = hit.from(RuleHitEntity.class);
				hit.select(cb.literal(1)).where(cb.equal(h.get("transaction"), t), cb.equal(h.get("ruleId"), ruleId));
				return cb.exists(hit);
			});
		}
		if (from != null) {
			specs.add((t, q, cb) -> cb.greaterThanOrEqualTo(t.get("occurredAt"), from));
		}
		if (to != null) {
			specs.add((t, q, cb) -> cb.lessThan(t.get("occurredAt"), to));
		}
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("transactionId")));
		return repository.findAll(Specification.allOf(specs), pageable).map(TransactionQueryService::toStored);
	}

	private static StoredTransaction toStored(TransactionEntity entity) {
		return new StoredTransaction(entity.toEvent(), entity.getReceivedAt(), entity.toEvaluation());
	}

}
