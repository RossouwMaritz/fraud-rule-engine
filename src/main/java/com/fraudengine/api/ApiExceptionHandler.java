package com.fraudengine.api;

import com.fraudengine.service.TransactionConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error is an RFC 9457 ProblemDetail. The base class covers 400s for bad JSON or params, 404s and 405s. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	record FieldError(String field, String message) {
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ex.getBody()
			.setProperty("errors", ex.getFieldErrors()
				.stream()
				.map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
				.toList());
		return handleExceptionInternal(ex, ex.getBody(), headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ex.getBody()
			.setProperty("errors", ex.getParameterValidationResults()
				.stream()
				.flatMap(result -> result.getResolvableErrors()
					.stream()
					.map(e -> new FieldError(result.getMethodParameter().getParameterName(), e.getDefaultMessage())))
				.toList());
		return handleExceptionInternal(ex, ex.getBody(), headers, status, request);
	}

	// A stored ID with different details, or two customers racing to insert the same ID.
	@ExceptionHandler({ TransactionConflictException.class, DataIntegrityViolationException.class })
	ProblemDetail conflict() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"This transaction ID is already stored with different details");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception ex) {
		log.error("Unhandled error", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
	}

}
