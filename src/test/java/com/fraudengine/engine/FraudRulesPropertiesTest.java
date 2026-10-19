package com.fraudengine.engine;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FraudRulesPropertiesTest {

	// Loads the real application.yml, so these tests cover the shipped defaults.
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(new ConfigDataApplicationContextInitializer())
		.withUserConfiguration(Config.class);

	@Test
	void defaultsBind() {
		runner.run(context -> {
			var properties = context.getBean(FraudRulesProperties.class);
			assertThat(properties.bands()).isEqualTo(new FraudRulesProperties.Bands(40, 80));
			assertThat(properties.rules().amountAnomaly().lookback()).isEqualTo(Duration.ofDays(30));
			assertThat(properties.rules().highRiskCategory().categories()).containsExactlyInAnyOrder("GAMBLING",
					"CRYPTO", "MONEY_TRANSFER");
		});
	}

	@Test
	void bandsOutOfOrderFailStartup() {
		runner.withPropertyValues("fraud.bands.review-from=90")
			.run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("high-risk-from"));
	}

	@Test
	void invalidNestedRuleConfigFailsStartup() {
		runner.withPropertyValues("fraud.rules.velocity.max-count=0")
			.run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("maxCount"));
	}

	@Test
	void zeroWindowFailsStartup() {
		runner.withPropertyValues("fraud.rules.velocity.window=0")
			.run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("window"));
	}

	@EnableConfigurationProperties(FraudRulesProperties.class)
	static class Config {

	}

}
