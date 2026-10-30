package com.fraudengine.api;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rejects a body over {@code fraud.max-request-body} with 413 before anything reads it. Tomcat has no such limit
 * for JSON, only for form posts. Runs after {@link RequestIdFilter}, so the error has a request ID, and before
 * security, so an oversized body is never parsed.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RequestSizeFilter extends OncePerRequestFilter {

	private final long maxBodyBytes;

	private final JsonMapper jsonMapper;

	RequestSizeFilter(@Value("${fraud.max-request-body}") DataSize maxBody, JsonMapper jsonMapper) {
		this.maxBodyBytes = maxBody.toBytes();
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// A chunked body has no length to check up front, so callers must send Content-Length.
		// shortcut: no byte counting while reading, so HTTP/2 without Content-Length isn't capped; count bytes if HTTP/2 is enabled.
		if (request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
			reject(response, HttpStatus.LENGTH_REQUIRED, "Send the body with a Content-Length header, not chunked");
		}
		else if (request.getContentLengthLong() > maxBodyBytes) {
			reject(response, HttpStatus.CONTENT_TOO_LARGE,
					"The body is over the limit of " + maxBodyBytes + " bytes");
		}
		else {
			chain.doFilter(request, response);
		}
	}

	private void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
	}

}
