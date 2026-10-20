package com.fraudengine;

import java.time.Duration;

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

	/** Lets {@code @PastOrPresent} accept times up to 5 minutes ahead, for clock skew between systems. */
	@Bean
	ValidationConfigurationCustomizer clockSkewTolerance() {
		return config -> config.addProperty("hibernate.validator.temporal_validation_tolerance",
				String.valueOf(Duration.ofMinutes(5).toMillis()));
	}

}
