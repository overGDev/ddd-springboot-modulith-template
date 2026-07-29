-- Sets up the two roles this project uses instead of one shared
-- credential, so DDL (schema changes) and DML (application queries)
-- are handled by different roles with different privileges:
--
--   ddd_migrator - owns the schema, runs Flyway migrations (DDL rights)
--   ddd_app      - used by the Spring Boot app at runtime (DML rights only)
--
-- Run as the postgres superuser, e.g. on Fedora:
--   sudo -iu postgres psql -f scripts/db/setup-roles.sql
--
-- Passwords are read from environment variables so nothing sensitive
-- ends up in shell history or this file. Set them before running:
--   export DDD_MIGRATOR_PASSWORD='...'
--   export DDD_APP_PASSWORD='...'
-- (use the same values you put in your local .env, see .env.example)

\set migrator_pw `echo "${DDD_MIGRATOR_PASSWORD:?DDD_MIGRATOR_PASSWORD not set}"`
\set app_pw `echo "${DDD_APP_PASSWORD:?DDD_APP_PASSWORD not set}"`

CREATE ROLE ddd_migrator WITH LOGIN PASSWORD :'migrator_pw';
CREATE ROLE ddd_app WITH LOGIN PASSWORD :'app_pw';

-- ddd_migrator owns the database, so it can create/alter/drop objects
-- in it (that's what Flyway needs to run migrations).
CREATE DATABASE ddd_template OWNER ddd_migrator;

\connect ddd_template

-- ddd_app only needs to connect and use the public schema - it does not
-- own anything here, so by default it has no privileges on the tables
-- ddd_migrator creates (ownership does not imply privileges in Postgres).
GRANT CONNECT ON DATABASE ddd_template TO ddd_app;
GRANT USAGE ON SCHEMA public TO ddd_app;

-- Grant DML on every table/sequence ddd_migrator creates from now on,
-- automatically. Without this, each new Flyway migration would require
-- a manual GRANT before ddd_app could use the new table.
ALTER DEFAULT PRIVILEGES FOR ROLE ddd_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ddd_app;

ALTER DEFAULT PRIVILEGES FOR ROLE ddd_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ddd_app;
