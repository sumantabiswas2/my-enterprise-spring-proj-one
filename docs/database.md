# Database Guide

## Ownership

Each business service owns one PostgreSQL database and its schema. Gateway and Discovery are stateless; Keycloak owns its separate identity database. No service reads another service database, shares a schema, uses cross-service foreign keys, or performs a distributed transaction.

Cross-context references are IDs only. A service obtains foreign information through an authorized API or an event-maintained local read model.

## Persistence boundary

The domain and application layers must not depend on JPA. Put JPA entities, Spring Data repositories, and persistence mappers under `adapter/out/persistence`; map them to domain models at the adapter boundary. Repository interfaces used by the application belong in `application/port/out`.

Use a table name that is not a reserved SQL keyword: for example, Order Service uses `orders`, not `order`.

## Schema design

- Give every table a deliberate primary key. Internal aggregate tables may use `BIGINT` identities; externally referenced event/outbox identifiers are UUIDs. Do not assume one key type fits every table.
- Store timestamps in UTC (`timestamptz`), model money with `numeric(19,4)` or integer minor units, and constrain finite states with a check constraint or PostgreSQL enum.
- Add indexes for actual query patterns, including foreign-reference columns owned locally, state/time searches, and unique business keys.
- Use optimistic locking or guarded update statements for concurrent reservation changes. Never allow available inventory below zero.
- Avoid `SELECT *`; select only needed columns and use pagination for collections.

## Flyway

Use versioned Flyway migrations in each owning service. A migration is immutable once applied: never edit, delete, or reuse its version. Add a new migration for every correction. `flyway migrate` is safe to run repeatedly because Flyway records applied versioned migrations; individual migration scripts do not need artificial `IF NOT EXISTS` clauses that hide mistakes.

Suggested layout:

```
src/main/resources/db/migration/
  V1__initial_schema.sql
  V2__add_customer_preferences.sql
```

Use repeatable migrations only for intentionally replaceable objects such as views, never for business-data changes.

## Reliable events

Outbox is a current requirement, not a future enhancement. Each event-producing business service has an `outbox_event` table written in the same transaction as the domain change. It includes at least `id`, aggregate type/ID, event type/version, payload, trace context, occurred time, publish status, and attempt metadata. A relay locks or claims rows safely, publishes to Kafka, and records acknowledgement without losing committed records.

Each event consumer has a durable `processed_event` table (consumer scope, `event_id`, processed time and optional source metadata) with a uniqueness constraint. Record deduplication and the local side effect in one transaction where possible.

Retention/cleanup jobs must be safe, observable, and preserve enough history for troubleshooting and replay policy.

## Transactions and performance

Transactions are local, short, and scoped to one service database. Use `@Transactional` at application-service boundaries, not controllers. Fetch strategy is query-specific: avoid accidental lazy-loading/N+1 behavior, but do not globally force eager loading. Use projections or explicit fetch joins for read use cases.

Connection settings, pool limits, migration configuration, and credentials are externalized. Database health contributes to readiness; a temporary Kafka or cache problem must not corrupt committed local state.

## Tests

Use disposable PostgreSQL integration tests (for example Testcontainers) for migrations, constraints, locking, outbox relay persistence, and deduplication. Test a duplicate event and an out-of-order terminal order event explicitly.
