# Testing

A test is any code that runs in the build and fails it when a claim stops being true. That covers the obvious kind, where the claim is about behaviour, and it also covers claims about structure, configuration, and provisioning. This template leans on the second kind more than most, because most of what it ships is scaffolding rather than features.

The general rule here: if something has to stay true for the template to work, the build should be what tells you it stopped.

## Running them

```sh
./mvnw verify   # everything
./mvnw test     # only the tests that need no database
```

`verify` is the whole suite. `test` is the fast half of it, for the loop where you are writing domain logic and don't want to wait on a container.

Docker has to be available for `verify`. The suite starts its own Postgres in a container, so nothing needs to be installed, configured, or running first, and `.env` is never read. A clean clone passes on any machine with a working Docker daemon.

The first run pulls the Postgres image. After that the whole suite takes a few seconds, because one container is shared across every test class rather than started per class.

## How the build splits them

The suffix on a test class decides which plugin runs it and when.

| Suffix | Plugin | Phase | Needs Docker |
|---|---|---|---|
| `*Test` | Surefire | `test` | No |
| `*IT` | Failsafe | `integration-test` | Yes |

`verify` runs both phases in that order, so failsafe never starts a container if a surefire test already failed. Surefire on its own answers in about two seconds, which is what makes the split worth having.

Everything the template ships today is provisioning, so every test in it is an `*IT` and the surefire phase runs zero classes. The split is what makes the first test of a domain aggregate land in the fast tier by being named for it.

Abstract base classes are ignored by both plugins regardless of their name, which is why [`PostgresIntegrationTest`](../src/test/java/com/example/dddspringbootmodulithtemplate/support/PostgresIntegrationTest.java) keeps its suffix without ever being collected as a test.

## The harness

[`PostgresIntegrationTest`](../src/test/java/com/example/dddspringbootmodulithtemplate/support/PostgresIntegrationTest.java) is the base class for anything that needs a database. It starts one container for the suite, provisions it, and hands Spring the connection details through `@DynamicPropertySource`.

The provisioning step is the part worth knowing about. Rather than creating roles inline, it copies [`scripts/db/setup-roles.sql`](../scripts/db/setup-roles.sql) into the container and runs it through `psql` — the same file, invoked the same way, as a real deployment. If that script breaks, the build breaks.

That mattered enough to shape the design. A test that defined its own roles would verify that some set of GRANTs produces a restricted role, and would keep passing while the script every new user actually runs was broken. The script is on the critical path of the only onboarding flow the template has, which makes it worth covering.

It goes through `psql` rather than JDBC because the script uses `\set` and `\connect`, which are psql meta-commands, and because `CREATE DATABASE` can't run inside a transaction block.

Datasource properties come from the container at runtime. The properties in `application.properties` have no fallback defaults — a missing `DB_URL` in production should fail loudly — so tests supply real values for a real database instead of weakening that.

## What's covered

| Test | Claim |
|---|---|
| [`FlywayMigrationIT`](../src/test/java/com/example/dddspringbootmodulithtemplate/platform/FlywayMigrationIT.java) | Every migration applies cleanly to an empty database, and the event publication registry exists |
| [`DatabaseRoleSeparationIT`](../src/test/java/com/example/dddspringbootmodulithtemplate/platform/DatabaseRoleSeparationIT.java) | `ddd_app` connects as itself, can read and write rows, and cannot create or drop tables |
| `ApplicationLaunchIT` | The context starts against the migrated schema |

`DatabaseRoleSeparationIT` injects the application's own `DataSource`, so it exercises the exact credential the app uses at runtime.

The case it exists for: `ALTER DEFAULT PRIVILEGES` in the provisioning script covers tables and sequences in `public`. A later migration that creates a schema, a function, or a type leaves `ddd_app` without access to it, and nothing else would catch that before someone hit it in production.

If you rename the roles or the database in `setup-roles.sql`, the constants in `PostgresIntegrationTest` and the values in `.env.example` have to move with them.

## Where tests go

Tests mirror the package they exercise. Packages that mirror nothing are the ones testing what doesn't live in `src/main/java`, and they're named for what they do cover.

```
src/test/java/com/example/dddspringbootmodulithtemplate/
├── ApplicationLaunchIT     mirrors the root package
├── platform/               schema, migrations, database roles
└── support/                shared fixtures, no tests
```

`platform` holds tests of the substrate every module runs on. It's deliberately not called `infrastructure`, since that name belongs to the layer inside each module and the two mean different things.

`support` holds machinery tests are built from. Nothing in it runs on its own.

## In CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) mirrors the two tiers as two jobs, so the cheap half runs far more often than the expensive one.

| Job | Runs | On |
|---|---|---|
| `fast-tests` | `./mvnw test` | Every push, on any branch |
| `build-and-test` | `./mvnw verify` | Pull requests, and pushes to `main` and `develop` |

`fast-tests` also compiles both source sets, so a push that doesn't compile fails in well under a minute without waiting on a container. That is most of its value while the surefire tier holds no tests.

Neither job lists the tests it runs, so adding one changes what CI does without touching the YAML.

GitHub's hosted runners already have a Docker daemon, so Testcontainers works there with no extra setup. The workflow declares no `services:` block, because the tests provision their own database — the same code path a developer runs locally, on the same script.

A pull request opened from a branch in this repository would otherwise run `fast-tests` twice, once for the push and once for the pull request. The job skips the pull request event when the head branch lives in this repository, which leaves forks covered.

Require `build-and-test` as the status check in a branch ruleset. `verify` runs surefire too, so requiring `fast-tests` as well adds no coverage.
