package com.fraudengine;

import java.time.Instant;

import com.fraudengine.engine.Channel;
import com.fraudengine.engine.TransactionEvent;
import com.fraudengine.service.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts the app twice on one database with a different HIGH_AMOUNT limit, as a config change and restart would. */
class RuleConfigVersionIT {

	@Test
	void eachDecisionPointsAtTheConfigItWasMadeWith() {
		try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
			postgres.start();
			var first = evaluateOnce(postgres, "5000000", "txn_cfg_1");
			var second = evaluateOnce(postgres, "6000000", "txn_cfg_2");

			assertThat(first).matches("[0-9a-f]{12}").isNotEqualTo(second);
			var jdbc = JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
					postgres.getPassword()));
			assertThat(jdbc.sql("""
					select t.transaction_id, c.config #>> '{rules,highAmount,thresholdsMinor,ZAR}'
					from transactions t join rule_configs c on c.version = t.config_version
					order by t.transaction_id""").query((rs, i) -> rs.getString(1) + "=" + rs.getString(2)).list())
				.containsExactly("txn_cfg_1=5000000", "txn_cfg_2=6000000");
		}
	}

	/** Runs the app with this ZAR limit, scores one transaction, and returns the config version it was stored with. */
	private static String evaluateOnce(PostgreSQLContainer postgres, String zarLimit, String transactionId) {
		// Command-line args, not properties(): those are defaults, and application.yml would win over them.
		try (var context = new SpringApplicationBuilder(FraudRuleEngineApplication.class).profiles("test")
			.run("--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl(),
					"--spring.datasource.username=" + postgres.getUsername(),
					"--spring.datasource.password=" + postgres.getPassword(),
					"--fraud.rules.high-amount.thresholds-minor.ZAR=" + zarLimit)) {
			return context.getBean(EvaluationService.class)
				.evaluate(new TransactionEvent(transactionId, "cus_cfg", "card_cfg", 10_000, "ZAR", "mer_1",
						"GROCERIES", Channel.ONLINE, "ZA", Instant.parse("2026-10-19T10:00:00Z")))
				.configVersion();
		}
	}

}
