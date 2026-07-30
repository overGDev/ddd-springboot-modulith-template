# DDD Spring Boot Modulith Template

A starting point for building a modular monolith with Spring Boot, structured around domain-driven design. Spring Modulith enforces the module boundaries, Flyway owns the schema, and Postgres is the only supported database.

Java 25, Spring Boot 4.1, Spring Modulith 2.1.

## Requirements

Docker is needed to run the test suite. The tests start a throwaway Postgres container, so nothing needs to be installed or running beforehand, and they never touch a local database.

Running the application needs a local Postgres instance you provision yourself.

## Setting up a local database

The application uses two Postgres roles rather than one shared credential:

| Role | Privileges | Used by |
|---|---|---|
| `ddd_migrator` | Owns the schema, full DDL | Flyway, at startup |
| `ddd_app` | `SELECT`, `INSERT`, `UPDATE`, `DELETE` | The application, at runtime |

The runtime credential cannot create, alter, or drop tables. A leaked credential or an injection bug can tamper with data; it cannot destroy the schema.

[`scripts/db/setup-roles.sql`](scripts/db/setup-roles.sql) creates both roles and the database. It reads the passwords from the environment so they stay out of your shell history. Run it by using:

```sh
export DDD_MIGRATOR_PASSWORD='...'
export DDD_APP_PASSWORD='...'
sudo -iu postgres psql -f scripts/db/setup-roles.sql
```

Then copy [`.env.example`](.env.example) to `.env` and fill in the same two passwords. `.env` is gitignored.

```sh
cp .env.example .env
```

The properties in `application.properties` have no fallback defaults. A missing `DB_URL` or `FLYWAY_URL` fails at startup rather than silently connecting somewhere unintended.

## Running

In VS Code, run the **DddSpringbootModulithTemplate (Spring Boot)** configuration. It automatically reads `.env` through the `envFile` setting in [`.vscode/launch.json`](.vscode/launch.json).

From a shell, export the variables yourself first — nothing loads `.env` implicitly:

```sh
set -a; source .env; set +a
./mvnw spring-boot:run
```

Both paths end up with the same variables resolved.

Flyway applies any pending migrations from `src/main/resources/db/migration` at startup, connecting as `ddd_migrator`. Hibernate is set to `validate`, so the schema belongs to the migrations and entities are checked against it.

## Tests

In VS Code, use the Testing view or the gutter icons next to each test. From a shell:

```sh
./mvnw verify   # everything
./mvnw test     # only the tests that need no database
```

Classes named `*IT` need Docker and run under failsafe, in `verify`. Classes named `*Test` run under surefire, in `test`, and never touch a container.

The suite starts one Postgres container, runs `setup-roles.sql` inside it to create the roles, migrates the fresh database, and shares that container across every test class. The first run pulls the Postgres image.

Because the tests execute the real `setup-roles.sql`, breaking the privilege model there turns the build red.

> `DatabaseRoleSeparationIT` connects with the application's own datasource and asserts that `ddd_app` can write rows and cannot change the schema.

If you rename the roles or the database in `setup-roles.sql`, update the matching constants in `PostgresIntegrationTest` and the values in `.env.example`.

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs `test` on every push to any branch, and `verify` on pull requests and on pushes to `main` and `develop`.

For further explanation on tests, check [docs/testing.md](docs/testing.md).

## Layout

```
ddd-springboot-modulith-template/
├── .github/workflows/      CI
├── deploy/examples/        reference configs for Nginx and PgBouncer
├── docs/                   design notes
├── scripts/db/             database provisioning
└── src/
    ├── main/
    │   ├── java/           application code, one package per module
    │   └── resources/db/   Flyway migrations
    └── test/java/
        ├── platform/       tests of the schema, migrations, and database roles
        └── support/        shared test fixtures
```

## Further reading

[docs/scaling.md](docs/scaling.md) covers when to introduce a load balancer and a connection pooler, and the prepared-statement tradeoff that comes with PgBouncer's transaction pooling.

[docs/testing.md](docs/testing.md) covers the test harness, what the platform tests assert, where tests live, how the build splits them by cost, and how CI runs them.
