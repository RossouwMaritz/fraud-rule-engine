package com.fraudengine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** A real server on both ports, since MockMvc only reaches the API port. The last test stops this context's Postgres. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ManagementServerIT {

	private final HttpClient http = HttpClient.newHttpClient();

	@LocalServerPort
	int apiPort;

	@LocalManagementPort
	int managementPort;

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	@Order(1)
	void probesAnswerOnTheManagementPortOnlyAndNeedNoKey() throws Exception {
		assertThat(get(managementPort, "/actuator/health/liveness")).isEqualTo(200);
		assertThat(get(managementPort, "/actuator/health/readiness")).isEqualTo(200);
		assertThat(get(managementPort, "/actuator/health")).isEqualTo(200);
		assertThat(get(managementPort, "/actuator/prometheus")).isEqualTo(401);
		assertThat(get(apiPort, "/actuator/health")).isEqualTo(404);
	}

	@Test
	@Order(2)
	void readinessGoesDownWhenPostgresStopsButLivenessStaysUp() throws Exception {
		postgres.stop();
		assertThat(get(managementPort, "/actuator/health/readiness")).isEqualTo(503);
		assertThat(get(managementPort, "/actuator/health/liveness")).isEqualTo(200);
	}

	private int get(int port, String path) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

}
