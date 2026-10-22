package com.fraudengine.api;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

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

	private static DataIntegrityViolationException violation(String constraint) {
		return new DataIntegrityViolationException("insert failed",
				new ConstraintViolationException("insert failed", new SQLException(), constraint));
	}

}
