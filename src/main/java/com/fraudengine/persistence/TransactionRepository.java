package com.fraudengine.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fraudengine.engine.AmountStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

	/** The customer's transactions with {@code from <= occurredAt <= to}, newest first. */
	@Query("""
			select t from TransactionEntity t
			where t.customerId = :customerId and t.occurredAt between :from and :to
			order by t.occurredAt desc""")
	List<TransactionEntity> findRecent(String customerId, Instant from, Instant to);

	/** The customer's latest card-present transaction strictly before {@code before}. */
	@Query("""
			select t from TransactionEntity t
			where t.customerId = :customerId and t.channel = com.fraudengine.engine.Channel.CARD_PRESENT
			  and t.occurredAt < :before
			order by t.occurredAt desc
			limit 1""")
	Optional<TransactionEntity> findPreviousCardPresent(String customerId, Instant before);

	/** Amount stats over {@code from <= occurredAt < to}. */
	@Query(value = """
			select count(*) as count,
			       cast(coalesce(avg(amount_minor), 0) as double precision) as mean,
			       cast(coalesce(stddev_samp(amount_minor), 0) as double precision) as stddev
			from transactions
			where customer_id = :customerId and occurred_at >= :from and occurred_at < :to""", nativeQuery = true)
	AmountStats amountStats(String customerId, Instant from, Instant to);

}
