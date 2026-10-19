package com.fraudengine.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "rule_hits")
public class RuleHitEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "transaction_id")
	private TransactionEntity transaction;

	private String ruleId;

	private int score;

	private String reason;

	protected RuleHitEntity() {
	}

	public RuleHitEntity(String ruleId, int score, String reason) {
		this.ruleId = ruleId;
		this.score = score;
		this.reason = reason;
	}

	void setTransaction(TransactionEntity transaction) {
		this.transaction = transaction;
	}

	public Long getId() {
		return id;
	}

	public String getRuleId() {
		return ruleId;
	}

	public int getScore() {
		return score;
	}

	public String getReason() {
		return reason;
	}

}
