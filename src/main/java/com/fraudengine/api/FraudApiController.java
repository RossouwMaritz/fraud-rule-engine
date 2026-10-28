package com.fraudengine.api;

import java.net.URI;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import com.fraudengine.engine.Decision;
import com.fraudengine.engine.FraudRulesProperties;
import com.fraudengine.service.EvaluationService;
import com.fraudengine.service.RuleConfigVersion;
import com.fraudengine.service.StoredTransaction;
import com.fraudengine.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = SecurityConfig.OPENAPI_SCHEME)
public class FraudApiController {

	private final EvaluationService evaluations;

	private final TransactionQueryService queries;

	private final FraudRulesProperties rules;

	private final RuleConfigVersion configVersion;

	private final ApiProperties api;

	public FraudApiController(EvaluationService evaluations, TransactionQueryService queries,
			FraudRulesProperties rules, RuleConfigVersion configVersion, ApiProperties api) {
		this.evaluations = evaluations;
		this.queries = queries;
		this.rules = rules;
		this.configVersion = configVersion;
		this.api = api;
	}

	/** 201 for a new evaluation, 200 for a replay of a stored one. */
	@PostMapping("/transactions")
	public ResponseEntity<EvaluationResponse> evaluate(@Valid @RequestBody TransactionRequest request) {
		var maxAge = rules.maxEventAge();
		if (request.occurredAt().isBefore(Instant.now().minus(maxAge))) {
			throw invalidField("occurredAt", "must be at most %d days old".formatted(maxAge.toDays()));
		}
		if (Currency.getAvailableCurrencies().stream().noneMatch(c -> c.getCurrencyCode().equals(request.currency()))) {
			throw invalidField("currency", "must be an ISO 4217 currency code");
		}
		var result = evaluations.evaluate(request.toEvent());
		var body = EvaluationResponse.of(result);
		if (result.replay()) {
			return ResponseEntity.ok(body);
		}
		return ResponseEntity.created(URI.create("/api/v1/transactions/" + result.transactionId())).body(body);
	}

	// The same 400 body as a validation error, so callers handle one shape.
	private static ErrorResponseException invalidField(String field, String message) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request content.");
		problem.setProperty("errors", List.of(new ApiExceptionHandler.FieldError(field, message)));
		return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
	}

	@GetMapping("/transactions/{id}")
	public StoredTransaction transaction(@PathVariable String id) {
		return queries.find(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction " + id + " not found"));
	}

	@GetMapping("/flags")
	public PagedModel<StoredTransaction> flags(@RequestParam(required = false) String customerId,
			@RequestParam(required = false) Decision decision, @RequestParam(required = false) String ruleId,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "${fraud.api.default-page-size}") @Min(1) int size) {
		if (size > api.maxPageSize()) {
			throw invalidField("size", "must be less than or equal to " + api.maxPageSize());
		}
		return new PagedModel<>(queries.flags(customerId, decision, ruleId, from, to, page, size));
	}

	@GetMapping("/rules")
	public RulesResponse rules() {
		return new RulesResponse(configVersion.version(), rules.bands(), rules.rules());
	}

}
