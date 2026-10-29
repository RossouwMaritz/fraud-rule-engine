package com.fraudengine.api;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void duplicateTransactionIdIs409() {
		assertThat(handler.integrityViolation(violation("transactions_pkey")).getStatus())
			.isEqualTo(HttpStatus.CONFLICT.value());
	}

	@Test
	void anyOtherConstraintIs500() {
		assertThat(handler.integrityViolation(violation("transactions_amount_minor_check")).getStatus())
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
		assertThat(handler.integrityViolation(new DataIntegrityViolationException("no cause")).getStatus())
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
	}

	// The lock timeout is covered end to end in FraudApiControllerTest.
	@Test
	void statementTimeoutDeadlockAndNoFreeConnectionAre503WithRetryAfter() {
		for (var ex : new Exception[] { new QueryTimeoutException("statement timeout"),
				new CannotAcquireLockException("deadlock"),
				new CannotCreateTransactionException("Connection is not available, request timed out") }) {
			var response = handler.unavailable(ex);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
			assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
		}
	}

	private static DataIntegrityViolationException violation(String constraint) {
		return new DataIntegrityViolationException("insert failed",
				new ConstraintViolationException("insert failed", new SQLException(), constraint));
	}

}
