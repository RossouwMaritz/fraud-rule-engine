package com.fraudengine.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request whose {@code X-API-Key} matches any configured key. Any other request passes through
 * unauthenticated, so open paths still work and protected ones get the 401 from {@link SecurityConfig}. More than
 * one key allows rotation: add the new key, move callers over, then remove the old one.
 */
class ApiKeyFilter extends OncePerRequestFilter {

	static final String HEADER = "X-API-Key";

	// 32 random characters is well past guessing range, and rules out placeholders like "changeme".
	static final int MIN_KEY_LENGTH = 32;

	private final List<byte[]> apiKeys;

	ApiKeyFilter(List<String> apiKeys) {
		Assert.notEmpty(apiKeys, "Set FRAUD_API_KEYS (comma-separated) or FRAUD_API_KEY");
		apiKeys.forEach(key -> Assert.isTrue(key.length() >= MIN_KEY_LENGTH,
				"Every API key must be at least " + MIN_KEY_LENGTH + " characters"));
		this.apiKeys = apiKeys.stream().map(key -> key.getBytes(StandardCharsets.UTF_8)).toList();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		var given = request.getHeader(HEADER);
		if (given != null && matchesAny(given.getBytes(StandardCharsets.UTF_8))) {
			SecurityContextHolder.getContext()
				.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("api-client", null,
						AuthorityUtils.NO_AUTHORITIES));
		}
		chain.doFilter(request, response);
	}

	// Constant time, and every key is checked, so response timing reveals neither how much of a key was right nor
	// which key matched.
	private boolean matchesAny(byte[] given) {
		var match = false;
		for (var key : apiKeys) {
			match |= MessageDigest.isEqual(key, given);
		}
		return match;
	}

}
