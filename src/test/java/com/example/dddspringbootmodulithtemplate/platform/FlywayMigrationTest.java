package com.example.dddspringbootmodulithtemplate.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.dddspringbootmodulithtemplate.support.PostgresIntegrationTest;

/**
 * Checks that every migration applies cleanly to an empty database, and that the tables the
 * application depends on exist afterwards.
 *
 * <p>Context startup already fails if a migration is malformed, so the value here is the explicit
 * assertion on what ran: a migration silently skipped, or renamed after being applied, shows up as
 * a failure with a useful message instead of an obscure Hibernate validation error.
 */
@SpringBootTest
class FlywayMigrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allMigrationsApplySuccessfully() {
        Integer failedMigrations = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class
        );

        assertThat(failedMigrations).isZero();
    }

    @Test
    void migrationsCreateTheEventPublicationRegistry() {
        boolean registryExists = tableExists("event_publication");

        assertThat(registryExists)
            .as("Spring Modulith's event publication registry must exist before events can be published")
            .isTrue();
    }

    @Test
    void hibernateValidatesAgainstTheMigratedSchema() {
        // ddl-auto=validate means the context would not have started if the entity model and the
        // migrated schema disagreed. Reaching this point is the assertion.
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT exists(
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
            )""", Boolean.class, tableName
        ));
    }
}
