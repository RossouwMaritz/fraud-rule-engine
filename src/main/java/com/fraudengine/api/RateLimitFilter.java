package com.fraudengine.api;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * A token bucket per API key: {@code burst} requests at once, refilled at {@code perSecond}. Over the limit gets 429
 * with {@code Retry-After}. Runs after {@link ApiKeyFilter} and only counts authenticated requests, so there is one
 * bucket per configured key and a caller can't grow the map by sending made-up keys.
 * <p>
 * shortcut: per-instance limit, move to gateway or Redis-backed limiter when running >1 replica.
 */
class RateLimitFilter extends OncePerRequestFilter {

	private final double perSecond;

	private final double burst;

	private final JsonMapper jsonMapper;

	private final AuthenticationTrustResolverImpl trustResolver = new AuthenticationTrustResolverImpl();

	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	RateLimitFilter(double perSecond, int burst, JsonMapper jsonMapper) {
		Assert.isTrue(perSecond > 0, "fraud.rate-limit.per-second (env FRAUD_RATE_LIMIT_PER_SECOND) must be positive");
		Assert.isTrue(burst >= 1, "fraud.rate-limit.burst (env FRAUD_RATE_LIMIT_BURST) must be at least 1");
		this.perSecond = perSecond;
		this.burst = burst;
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// Anonymous counts as not authenticated: no valid key, so the 401 comes later.
		if (!trustResolver.isAuthenticated(SecurityContextHolder.getContext().getAuthentication())) {
			chain.doFilter(request, response);
			return;
		}
		var bucket = buckets.computeIfAbsent(request.getHeader(ApiKeyFilter.HEADER), k -> new Bucket(burst));
		var waitSeconds = bucket.take(perSecond, burst, System.nanoTime());
		if (waitSeconds == 0) {
			chain.doFilter(request, response);
			return;
		}
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(waitSeconds));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
				"Over the limit of " + perSecond + " requests per second for this API key"));
	}

	static final class Bucket {

		private double tokens;

		private long lastNanos;

		Bucket(double tokens) {
			this(tokens, System.nanoTime());
		}

		Bucket(double tokens, long nowNanos) {
			this.tokens = tokens;
			this.lastNanos = nowNanos;
		}

		/** Takes a token and returns 0, or returns the whole seconds until one is free. */
		synchronized long take(double perSecond, double burst, long nowNanos) {
			tokens = Math.min(burst, tokens + (nowNanos - lastNanos) * perSecond / 1e9);
			lastNanos = nowNanos;
			if (tokens >= 1) {
				tokens--;
				return 0;
			}
			return (long) Math.ceil((1 - tokens) / perSecond);
		}

	}

}
