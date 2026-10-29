package com.fraudengine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class FraudRuleEngineApplicationTests {

	@Autowired
	JdbcClient jdbc;

	@Test
	void contextLoads() {
	}

	// A misspelt Hikari key is silently ignored, which would leave statements unbounded.
	@Test
	void everyConnectionHasTheStatementTimeout() {
		assertThat(jdbc.sql("show statement_timeout").query(String.class).single()).isEqualTo("5s");
	}

}
