package com.fraudengine.engine;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

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
			assertThat(properties.maxEventAge()).isEqualTo(Duration.ofDays(45));
			assertThat(properties.rules().amountAnomaly().lookback()).isEqualTo(Duration.ofDays(30));
			assertThat(properties.rules().amountAnomaly().repeatMinAge()).isEqualTo(Duration.ofDays(25));
			assertThat(properties.rules().amountAnomaly().repeatMaxAge()).isEqualTo(Duration.ofDays(35));
			assertThat(properties.rules().highAmount().thresholdsMinor()).isEqualTo(
					Map.of("ZAR", 5_000_000L, "USD", 300_000L, "EUR", 300_000L, "GBP", 250_000L));
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
	void repeatWindowOutOfOrderFailsStartup() {
		runner.withPropertyValues("fraud.rules.amount-anomaly.repeat-min-age=40d")
			.run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("repeat-max-age"));
	}

	@Test
	void invalidNestedRuleConfigFailsStartup() {
		runner.withPropertyValues("fraud.rules.velocity.max-count=0")
			.run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("maxCount"));
	}

	@Test
	void currencyLimitsCanBeChangedAndAddedByEnvVar() {
		runner.withInitializer(context -> context.getEnvironment()
			.getPropertySources()
			.addFirst(new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
					Map.of("FRAUD_RULES_HIGHAMOUNT_THRESHOLDSMINOR_ZAR", "100",
							"FRAUD_RULES_HIGHAMOUNT_THRESHOLDSMINOR_JPY", "500000"))))
			.run(context -> assertThat(context.getBean(FraudRulesProperties.class).rules().highAmount().thresholdsMinor())
				.isEqualTo(Map.of("ZAR", 100L, "USD", 300_000L, "EUR", 300_000L, "GBP", 250_000L, "JPY", 500_000L)));
	}

	@Test
	void namedEnvVarsOverrideTheDefaults() {
		runner.withInitializer(context -> context.getEnvironment()
			.getPropertySources()
			.addFirst(new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
					Map.of("FRAUD_VELOCITY_MAX_COUNT", "8", "FRAUD_COUNTRY_HOP_WINDOW", "3h",
							"FRAUD_HIGH_AMOUNT_ZAR", "100", "FRAUD_HIGH_RISK_CATEGORIES", "CRYPTO,ADULT"))))
			.run(context -> {
				var rules = context.getBean(FraudRulesProperties.class).rules();
				assertThat(rules.velocity().maxCount()).isEqualTo(8);
				assertThat(rules.countryHop().window()).isEqualTo(Duration.ofHours(3));
				assertThat(rules.highAmount().thresholdsMinor()).containsEntry("ZAR", 100L);
				assertThat(rules.highRiskCategory().categories()).containsExactlyInAnyOrder("CRYPTO", "ADULT");
			});
	}

	@Test
	void zeroOrNegativeCurrencyLimitFailsStartup() {
		runner.withPropertyValues("fraud.rules.high-amount.thresholds-minor.USD=0")
			.run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("thresholdsMinor"));
		runner.withPropertyValues("fraud.rules.high-amount.thresholds-minor.GBP=-1")
			.run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("thresholdsMinor"));
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
