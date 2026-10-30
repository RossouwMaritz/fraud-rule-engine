package com.fraudengine.persistence;

import java.util.List;

import com.fraudengine.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The fraud_app role from V4: it reads and adds rows, and nothing else. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class AppRoleTest {

	@Autowired
	TestEntityManager em;

	@Test
	void everyAppTableAllowsOnlySelectAndInsert() {
		@SuppressWarnings("unchecked")
		List<Object[]> rows = em.getEntityManager().createNativeQuery("""
				select table_name,
				       has_table_privilege('fraud_app', table_name, 'select'),
				       has_table_privilege('fraud_app', table_name, 'insert'),
				       has_table_privilege('fraud_app', table_name, 'update, delete, truncate, references, trigger')
				from information_schema.tables
				where table_schema = 'public'""").getResultList();

		assertThat(rows).hasSizeGreaterThan(1);
		for (var row : rows) {
			var history = row[0].equals("flyway_schema_history");
			assertThat(row).as("%s", row[0]).containsExactly(row[0], !history, !history, false);
		}
	}

	@Test
	void cannotDropATable() {
		var entityManager = em.getEntityManager();
		entityManager.createNativeQuery("set local role fraud_app").executeUpdate();

		// The test's transaction rolls back afterwards, so even a wrong result drops nothing for later tests.
		assertThatThrownBy(() -> entityManager.createNativeQuery("drop table transactions").executeUpdate())
			.rootCause()
			.hasMessageContaining("must be owner of table transactions");
	}

}
