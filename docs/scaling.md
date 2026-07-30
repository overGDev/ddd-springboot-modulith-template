# Scaling beyond a single instance

This project defaults to running as a **single application instance** talking
directly to Postgres for the following reasons:

- One instance, one HikariCP pool, one direct connection to Postgres is the
  simplest thing that works, and is enough for most side projects, portfolios,
  and early-stage products.
- Server-side prepared statements (PgJDBC's default `prepareThreshold=5`,
  left untouched in `.env.example`) give a real, free performance benefit
  when a connection is reused by the same client over and over - which is
  exactly what happens with a single instance and no pooler in front of
  Postgres.

Don't introduce a load balancer or a connection pooler until you actually
need to run more than one instance of the app. Adding them earlier only
adds operational surface area for a problem you don't have yet.

## When you actually need multiple instances

If you actually need more instances:

**1. Put Nginx in front of the app instances (load balancing)**

Each app instance keeps its own embedded Tomcat and HikariCP pool - nothing
changes inside the app. Nginx just distributes incoming HTTP traffic across
however many instances you're running.

See [`deploy/examples/nginx.conf`](../deploy/examples/nginx.conf) for a
minimal starting config.

**2. Put PgBouncer in front of Postgres (connection pooling)**

With N app instances, each holding its own HikariCP pool, the number of
real connections to Postgres is `N * spring.datasource.hikari.maximum-pool-size`.
That adds up fast and can exceed Postgres's `max_connections` (default 100).
PgBouncer sits between the app instances and Postgres and multiplexes many
logical connections onto a much smaller number of real ones.

See [`deploy/examples/pgbouncer.ini`](../deploy/examples/pgbouncer.ini) for
a minimal starting config using **transaction pooling mode**, the mode that
actually reduces real connection count (session pooling mode doesn't).

**3. Required app-side change: disable server-side prepared statements**

Transaction pooling mode hands a given physical Postgres connection to a
different client after every transaction. Server-side prepared statements
are cached per physical connection, so a statement prepared by one client
can collide with another client reusing that same connection right after -
Postgres raises `prepared statement "S_1" already exists`.

Once PgBouncer (transaction pooling) is in the picture, update `DB_URL` in
your `.env` to add `prepareThreshold=0` and point at PgBouncer's port
instead of Postgres directly:

```
# Before (single instance, direct to Postgres)
DB_URL=jdbc:postgresql://localhost:5432/ddd_template?sslmode=disable

# After (multiple instances, via PgBouncer)
DB_URL=jdbc:postgresql://localhost:6432/ddd_template?sslmode=disable&prepareThreshold=0
```

This is a real tradeoff, not a free upgrade: you're trading away server-side
prepared statement reuse in exchange for the ability to run many instances
without exhausting Postgres's connection limit. Don't make this trade until
you're actually running more than one instance.
