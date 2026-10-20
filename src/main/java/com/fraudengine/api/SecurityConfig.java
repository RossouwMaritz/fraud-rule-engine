package com.fraudengine.api;

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

/** Every endpoint needs the API key except health checks. Stateless, so there is no session or CSRF token. */
@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain apiKeySecurity(HttpSecurity http, @Value("${fraud.api-key}") String apiKey,
			JsonMapper jsonMapper) {
		Assert.hasText(apiKey, "fraud.api-key (env FRAUD_API_KEY) must not be blank");
		return http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated())
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
