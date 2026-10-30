package com.fraudengine;

import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Postgres set up as compose sets it up: the app connects as fraud_app, which may only read and add rows, and Flyway
 * migrates as the owner. So a query that needs more rights than production has fails here too.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final String APP_PASSWORD = "fraud-app-test";

	@Bean
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
			.withCopyFileToContainer(MountableFile.forHostPath("postgres/init-app-role.sh", 0755),
					"/docker-entrypoint-initdb.d/init-app-role.sh")
			.withEnv("POSTGRES_APP_PASSWORD", APP_PASSWORD);
	}

	@Bean
	JdbcConnectionDetails appConnection(PostgreSQLContainer postgres) {
		return new JdbcConnectionDetails() {
			@Override
			public String getJdbcUrl() {
				return postgres.getJdbcUrl();
			}

			@Override
			public String getUsername() {
				return "fraud_app";
			}

			@Override
			public String getPassword() {
				return APP_PASSWORD;
			}
		};
	}

	@Bean
	FlywayConnectionDetails ownerConnection(PostgreSQLContainer postgres) {
		return new FlywayConnectionDetails() {
			@Override
			public String getJdbcUrl() {
				return postgres.getJdbcUrl();
			}

			@Override
			public String getUsername() {
				return postgres.getUsername();
			}

			@Override
			public String getPassword() {
				return postgres.getPassword();
			}
		};
	}

}
