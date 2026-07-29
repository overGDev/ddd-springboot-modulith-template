# Testing

A test is any code that runs in the build and fails it when a claim stops being true. That covers the obvious kind, where the claim is about behaviour, and it also covers claims about structure, configuration, and provisioning. This template leans on the second kind more than most, because most of what it ships is scaffolding rather than features.

The general rule here: if something has to stay true for the template to work, the build should be what tells you it stopped.

## Running them

```sh
./mvnw test
```

Docker has to be available. The suite starts its own Postgres in a container, so nothing needs to be installed, configured, or running first, and `.env` is never read. A clean clone passes on any machine with a working Docker daemon.

The first run pulls the Postgres image. After that the whole suite takes a few seconds, because one container is shared across every test class rather than started per class.

## The harness

[`PostgresIntegrationTest`](../src/test/java/com/example/dddspringbootmodulithtemplate/support/PostgresIntegrationTest.java) is the base class for anything that needs a database. It starts one container for the suite, provisions it, and hands Spring the connection details through `@DynamicPropertySource`.

The provisioning step is the part worth knowing about. Rather than creating roles inline, it copies [`scripts/db/setup-roles.sql`](../scripts/db/setup-roles.sql) into the container and runs it through `psql` — the same file, invoked the same way, as a real deployment. If that script breaks, the build breaks.

That mattered enough to shape the design. A test that defined its own roles would verify that some set of GRANTs produces a restricted role, and would keep passing while the script every new user actually runs was broken. The script is on the critical path of the only onboarding flow the template has, which makes it worth covering.

It goes through `psql` rather than JDBC because the script uses `\set` and `\connect`, which are psql meta-commands, and because `CREATE DATABASE` can't run inside a transaction block.

Datasource properties come from the container at runtime. The properties in `application.properties` have no fallback defaults — a missing `DB_URL` in production should fail loudly — so tests supply real values for a real database instead of weakening that.

## What's covered

| Test | Claim |
|---|---|
| [`FlywayMigrationTest`](../src/test/java/com/example/dddspringbootmodulithtemplate/platform/FlywayMigrationTest.java) | Every migration applies cleanly to an empty database, and the event publication registry exists |
| [`DatabaseRoleSeparationTest`](../src/test/java/com/example/dddspringbootmodulithtemplate/platform/DatabaseRoleSeparationTest.java) | `ddd_app` connects as itself, can read and write rows, and cannot create or drop tables |
| `ApplicationLaunchTest` | The context starts against the migrated schema |

`DatabaseRoleSeparationTest` injects the application's own `DataSource`, so it exercises the exact credential the app uses at runtime.

The case it exists for: `ALTER DEFAULT PRIVILEGES` in the provisioning script covers tables and sequences in `public`. A later migration that creates a schema, a function, or a type leaves `ddd_app` without access to it, and nothing else would catch that before someone hit it in production.

If you rename the roles or the database in `setup-roles.sql`, the constants in `PostgresIntegrationTest` and the values in `.env.example` have to move with them.

## Where tests go

Tests mirror the package they exercise. Packages that mirror nothing are the ones testing what doesn't live in `src/main/java`, and they're named for what they do cover.

```
src/test/java/com/example/dddspringbootmodulithtemplate/
├── ApplicationLaunchTest   mirrors the root package
├── platform/               schema, migrations, database roles
└── support/                shared fixtures, no tests
```

`platform` holds tests of the substrate every module runs on. It's deliberately not called `infrastructure`, since that name belongs to the layer inside each module and the two mean different things.

`support` holds machinery tests are built from. Nothing in it runs on its own.
