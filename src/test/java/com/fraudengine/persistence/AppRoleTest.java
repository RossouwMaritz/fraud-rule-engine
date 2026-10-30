package com.fraudengine.persistence;

import java.util.List;

import com.fraudengine.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fraud_app role from V4: it reads and adds rows, and nothing else. The tests connect as fraud_app, so this lists
 * tables from pg_tables, which shows every table, not information_schema, which hides the ones the role can't use.
 */
@DataJpaTest
// Keep the Testcontainers Postgres: its connection isn't a @ServiceConnection, which @DataJpaTest would know to keep.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AppRoleTest {

	@Autowired
	TestEntityManager em;

	@Test
	void everyAppTableAllowsOnlySelectAndInsert() {
		@SuppressWarnings("unchecked")
		List<Object[]> rows = em.getEntityManager().createNativeQuery("""
				select tablename,
				       has_table_privilege('fraud_app', tablename, 'select'),
				       has_table_privilege('fraud_app', tablename, 'insert'),
				       has_table_privilege('fraud_app', tablename, 'update, delete, truncate, references, trigger')
				from pg_tables
				where schemaname = 'public'""").getResultList();

		assertThat(rows).hasSizeGreaterThan(1);
		for (var row : rows) {
			var history = row[0].equals("flyway_schema_history");
			assertThat(row).as("%s", row[0]).containsExactly(row[0], !history, !history, false);
		}
	}

	@Test
	void connectsAsTheAppRoleAndCannotDropATable() {
		var entityManager = em.getEntityManager();
		assertThat(entityManager.createNativeQuery("select current_user").getSingleResult()).isEqualTo("fraud_app");

		// The test's transaction rolls back afterwards, so even a wrong result drops nothing for later tests.
		assertThatThrownBy(() -> entityManager.createNativeQuery("drop table transactions").executeUpdate())
			.rootCause()
			.hasMessageContaining("must be owner of table transactions");
	}

}
