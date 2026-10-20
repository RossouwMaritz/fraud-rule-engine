package com.fraudengine.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request whose {@code X-API-Key} matches the configured key. Any other request passes through
 * unauthenticated, so open paths still work and protected ones get the 401 from {@link SecurityConfig}.
 */
class ApiKeyFilter extends OncePerRequestFilter {

	static final String HEADER = "X-API-Key";

	private final byte[] apiKey;

	ApiKeyFilter(String apiKey) {
		this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		var given = request.getHeader(HEADER);
		// Constant time, so response timing doesn't reveal how much of the key was right.
		if (given != null && MessageDigest.isEqual(apiKey, given.getBytes(StandardCharsets.UTF_8))) {
			SecurityContextHolder.getContext()
				.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("api-client", null,
						AuthorityUtils.NO_AUTHORITIES));
		}
		chain.doFilter(request, response);
	}

}
