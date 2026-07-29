package com.example.dddspringbootmodulithtemplate.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.dddspringbootmodulithtemplate.support.PostgresIntegrationTest;

/**
 * Asserts the DDL/DML role split actually holds, rather than trusting that the GRANTs in
 * setup-roles.sql say what we think they say.
 *
 * <p>The injected {@link DataSource} is the application's own runtime datasource, so these tests
 * exercise the exact credential the app uses in production - not a stand-in.
 *
 * <p>The case that makes this worth running: {@code ALTER DEFAULT PRIVILEGES} in setup-roles.sql
 * only covers tables and sequences in {@code public}. A future migration that creates a schema, a
 * function, or a type leaves {@code ddd_app} without access, and nothing else would catch it
 * before runtime.
 */
@SpringBootTest
class DatabaseRoleSeparationTest extends PostgresIntegrationTest {

    /** Postgres SQLSTATE for insufficient_privilege. */
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Autowired
    private DataSource runtimeDataSource;

    @Test
    void appRoleConnectsAsTheLeastPrivilegedRole() throws SQLException {
        try (Connection connection = runtimeDataSource.getConnection()) {
            assertThat(connection.getMetaData().getUserName()).isEqualTo(APP_USERNAME);
        }
    }

    @Test
    void appRoleCanReadAndWriteTablesCreatedByMigrations() {
        assertThatNoException().isThrownBy(() -> {
            try (
                Connection connection = runtimeDataSource.getConnection();
                Statement statement = connection.createStatement()
            ) {
                statement.executeUpdate("INSERT INTO flyway_setup_check DEFAULT VALUES");
                statement.executeQuery("SELECT id FROM flyway_setup_check");
            }
        });
    }

    @Test
    void appRoleCannotChangeTheSchema() {
        assertThatExceptionOfType(SQLException.class)
            .isThrownBy(() -> {
                try (
                    Connection connection = runtimeDataSource.getConnection();
                    Statement statement = connection.createStatement()
                ) {
                    statement.executeUpdate("CREATE TABLE should_not_be_creatable (id BIGINT)");
                }
            })
            .satisfies(exception -> assertThat(exception.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    @Test
    void appRoleCannotDropTablesCreatedByMigrations() {
        assertThatExceptionOfType(SQLException.class)
            .isThrownBy(() -> {
                try (
                    Connection connection = runtimeDataSource.getConnection();
                    Statement statement = connection.createStatement()
                ) {
                    statement.executeUpdate("DROP TABLE flyway_setup_check");
                }
            })
            .satisfies(exception -> assertThat(exception.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }
}
