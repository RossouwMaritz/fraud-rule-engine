package com.fraudengine.api;

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
import org.springframework.util.Assert;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every endpoint needs the API key except health checks and the API docs. Stateless, so there is no session or
 * CSRF token.
 */
@Configuration
@SecurityScheme(name = SecurityConfig.OPENAPI_SCHEME, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER,
		paramName = ApiKeyFilter.HEADER)
class SecurityConfig {

	/** Gives Swagger UI an Authorize button, so "Try it out" sends the key. */
	static final String OPENAPI_SCHEME = "apiKey";

	@Bean
	SecurityFilterChain apiKeySecurity(HttpSecurity http, @Value("${fraud.api-key}") String apiKey,
			JsonMapper jsonMapper) {
		Assert.hasText(apiKey, "fraud.api-key (env FRAUD_API_KEY) must not be blank");
		return http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// A browser can't add a header to a page load, so the docs must be open for Swagger UI to load.
			.authorizeHttpRequests(a -> a
				.requestMatchers("/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
				.permitAll()
				.anyRequest()
				.authenticated())
			.addFilterBefore(new ApiKeyFilter(apiKey), AuthorizationFilter.class)
			.exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
				response.setStatus(HttpStatus.UNAUTHORIZED.value());
				response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
				jsonMapper.writeValue(response.getOutputStream(), ProblemDetail
					.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Missing or invalid " + ApiKeyFilter.HEADER + " header"));
			}))
			.build();
	}

}
