package com.fraudengine.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fraudengine.engine.AmountStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository
		extends JpaRepository<TransactionEntity, String>, JpaSpecificationExecutor<TransactionEntity> {

	/** Blocks until no other transaction holds this customer's lock. Released at commit or rollback. */
	@Query(value = "select 1 from pg_advisory_xact_lock(hashtext(:customerId))", nativeQuery = true)
	int lockCustomer(String customerId);

	/** The customer's transactions with {@code from <= occurredAt <= to}, newest first. */
	@Query("""
			select t from TransactionEntity t
			where t.customerId = :customerId and t.occurredAt between :from and :to
			order by t.occurredAt desc""")
	List<TransactionEntity> findRecent(String customerId, Instant from, Instant to);

	/**
	 * The customer's latest card-present transaction at or before {@code upTo}. Equal times count, so two cards used
	 * at the same moment in different countries are caught. Call it before saving the current transaction.
	 */
	@Query("""
			select t from TransactionEntity t
			where t.customerId = :customerId and t.channel = com.fraudengine.engine.Channel.CARD_PRESENT
			  and t.occurredAt <= :upTo
			order by t.occurredAt desc
			limit 1""")
	Optional<TransactionEntity> findPreviousCardPresent(String customerId, Instant upTo);

	/** The customer's earliest card-present transaction strictly after {@code after}, for events that arrive late. */
	@Query("""
			select t from TransactionEntity t
			where t.customerId = :customerId and t.channel = com.fraudengine.engine.Channel.CARD_PRESENT
			  and t.occurredAt > :after
			order by t.occurredAt
			limit 1""")
	Optional<TransactionEntity> findNextCardPresent(String customerId, Instant after);

	/** Whether the customer paid this merchant this exact amount with {@code from <= occurredAt < to}. */
	@Query("""
			select count(t) > 0 from TransactionEntity t
			where t.customerId = :customerId and t.merchantId = :merchantId and t.amountMinor = :amountMinor
			  and t.currency = :currency and t.occurredAt >= :from and t.occurredAt < :to""")
	boolean existsRepeatPayment(String customerId, String merchantId, long amountMinor, String currency, Instant from,
			Instant to);

	/** Amount stats over {@code from <= occurredAt < to}. */
	@Query(value = """
			select count(*) as count,
			       cast(coalesce(avg(amount_minor), 0) as double precision) as mean,
			       cast(coalesce(stddev_samp(amount_minor), 0) as double precision) as stddev
			from transactions
			where customer_id = :customerId and occurred_at >= :from and occurred_at < :to""", nativeQuery = true)
	AmountStats amountStats(String customerId, Instant from, Instant to);

}
