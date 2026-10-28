package com.fraudengine;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

// The API key is the only login, so no default in-memory user with a generated password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class FraudRuleEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(FraudRuleEngineApplication.class, args);
	}

	/** Lets {@code @PastOrPresent} accept times up to {@code fraud.api.clock-skew} ahead. */
	// @Value, not ApiProperties, so test slices without config properties still start. ApiProperties validates it.
	@Bean
	ValidationConfigurationCustomizer clockSkewTolerance(@Value("${fraud.api.clock-skew}") Duration clockSkew) {
		return config -> config.addProperty("hibernate.validator.temporal_validation_tolerance",
				String.valueOf(clockSkew.toMillis()));
	}

}
