package com.fraudengine.api;

import java.util.List;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every endpoint needs the API key except health checks, and the API docs when they're on. Stateless, so there is no
 * session or CSRF token.
 */
@Configuration
@SecurityScheme(name = SecurityConfig.OPENAPI_SCHEME, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER,
		paramName = ApiKeyFilter.HEADER)
class SecurityConfig {

	/** Gives Swagger UI an Authorize button, so "Try it out" sends the key. */
	static final String OPENAPI_SCHEME = "apiKey";

	@Bean
	SecurityFilterChain apiKeySecurity(HttpSecurity http, @Value("${fraud.api-keys}") List<String> apiKeys,
			@Value("${fraud.docs-enabled}") boolean docsEnabled, JsonMapper jsonMapper) {
		return http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(a -> {
				a.requestMatchers("/actuator/health/**").permitAll();
				// A browser can't add a header to a page load, so the docs must be open for Swagger UI to load.
				// When they're off, their paths need the key like any other, so a caller without one learns nothing.
				if (docsEnabled) {
					a.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
				}
				a.anyRequest().authenticated();
			})
			.addFilterBefore(new ApiKeyFilter(apiKeys), AuthorizationFilter.class)
			.exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
				response.setStatus(HttpStatus.UNAUTHORIZED.value());
				response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
				jsonMapper.writeValue(response.getOutputStream(), ProblemDetail
					.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Missing or invalid " + ApiKeyFilter.HEADER + " header"));
			}))
			.build();
	}

}
