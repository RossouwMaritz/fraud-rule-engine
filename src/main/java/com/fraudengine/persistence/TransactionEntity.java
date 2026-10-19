package com.fraudengine.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fraudengine.engine.Channel;
import com.fraudengine.engine.Decision;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

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

	public void addHit(RuleHitEntity hit) {
		hit.setTransaction(this);
		hits.add(hit);
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
