package com.fraudengine.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fraudengine.engine.Channel;
import com.fraudengine.engine.Decision;
import com.fraudengine.engine.Evaluation;
import com.fraudengine.engine.RuleHit;
import com.fraudengine.engine.TransactionEvent;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

// Persistable so save() always inserts a new entity: a duplicate ID fails instead of merging over the stored result.
@Entity
@Table(name = "transactions")
public class TransactionEntity implements Persistable<String> {

	@Transient
	private boolean isNew = true;

	@Id
	private String transactionId;

	private String customerId;

	private long amountMinor;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 3)
	private String currency;

	private String merchantId;

	private String merchantCategory;

	@Enumerated(EnumType.STRING)
	private Channel channel;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 2)
	private String merchantCountry;

	private Instant occurredAt;

	// Set by the database default, read back after insert.
	@Generated
	@Column(insertable = false, updatable = false)
	private Instant receivedAt;

	private int totalScore;

	@Enumerated(EnumType.STRING)
	private Decision decision;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> rulesErrored = new ArrayList<>();

	@OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy
	private List<RuleHitEntity> hits = new ArrayList<>();

	protected TransactionEntity() {
	}

	public TransactionEntity(String transactionId, String customerId, long amountMinor, String currency,
			String merchantId, String merchantCategory, Channel channel, String merchantCountry, Instant occurredAt,
			int totalScore, Decision decision, List<String> rulesErrored) {
		this.transactionId = transactionId;
		this.customerId = customerId;
		this.amountMinor = amountMinor;
		this.currency = currency;
		this.merchantId = merchantId;
		this.merchantCategory = merchantCategory;
		this.channel = channel;
		this.merchantCountry = merchantCountry;
		this.occurredAt = occurredAt;
		this.totalScore = totalScore;
		this.decision = decision;
		this.rulesErrored = new ArrayList<>(rulesErrored);
	}

	public static TransactionEntity of(TransactionEvent txn, Evaluation evaluation) {
		var entity = new TransactionEntity(txn.transactionId(), txn.customerId(), txn.amountMinor(), txn.currency(),
				txn.merchantId(), txn.merchantCategory(), txn.channel(), txn.merchantCountry(), txn.occurredAt(),
				evaluation.totalScore(), evaluation.decision(), evaluation.rulesErrored());
		evaluation.hits().forEach(hit -> entity.addHit(new RuleHitEntity(hit.ruleId(), hit.score(), hit.reason())));
		return entity;
	}

	public void addHit(RuleHitEntity hit) {
		hit.setTransaction(this);
		hits.add(hit);
	}

	public TransactionEvent toEvent() {
		return new TransactionEvent(transactionId, customerId, amountMinor, currency, merchantId, merchantCategory,
				channel, merchantCountry, occurredAt);
	}

	public Evaluation toEvaluation() {
		return new Evaluation(totalScore, decision,
				hits.stream().map(hit -> new RuleHit(hit.getRuleId(), hit.getScore(), hit.getReason())).toList(),
				getRulesErrored());
	}

	@Override
	public String getId() {
		return transactionId;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		isNew = false;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public String getCustomerId() {
		return customerId;
	}

	public long getAmountMinor() {
		return amountMinor;
	}

	public String getCurrency() {
		return currency;
	}

	public String getMerchantId() {
		return merchantId;
	}

	public String getMerchantCategory() {
		return merchantCategory;
	}

	public Channel getChannel() {
		return channel;
	}

	public String getMerchantCountry() {
		return merchantCountry;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

	public int getTotalScore() {
		return totalScore;
	}

	public Decision getDecision() {
		return decision;
	}

	public List<String> getRulesErrored() {
		return List.copyOf(rulesErrored);
	}

	public List<RuleHitEntity> getHits() {
		return List.copyOf(hits);
	}

}
