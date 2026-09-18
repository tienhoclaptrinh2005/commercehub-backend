# Database migrations

CommerceHub uses Flyway as the only schema migration runner. Hibernate stays on
`ddl-auto=validate`; application code must never create or alter production tables.

## Migration layout

- `V1__baseline_schema.sql`: immutable full schema for a new empty database.
- `V2` through `V6`: migrations that were introduced before Flyway adoption.
- Every future schema change must be a new file such as
  `V7__add_example_column.sql`.

Never edit a migration after it has been applied. Flyway stores its checksum in
`flyway_schema_history` and intentionally rejects modified history.

## Existing local database (one-time onboarding)

1. Stop every backend instance and create a PostgreSQL backup.
2. Restore the backup into a temporary database and test migration there first.
3. Start the backend once with `FLYWAY_BASELINE_ON_MIGRATE=true`.
   Flyway records V1 as the baseline and applies V2 onward.
4. Verify that `flyway_schema_history` ends at the expected successful version.
5. Remove the override or set `FLYWAY_BASELINE_ON_MIGRATE=false` for every later run.

## New empty database

Use `FLYWAY_BASELINE_ON_MIGRATE=false`. Flyway executes V1 and all later versions,
then Hibernate validates the resulting schema.

## Staging and production

- Always set `FLYWAY_ENABLED=true` and `FLYWAY_BASELINE_ON_MIGRATE=false`.
- Back up the target database and rehearse restore before deployment.
- Run exactly the application artifact that was verified on the restored copy.
- Deploy one migration runner first; other instances may start after it succeeds.
- `clean` is disabled and must never be enabled in staging or production.
- Never run `seed-test-data.sql` outside an isolated local/test database.

If a versioned migration fails, stop the release. Do not manually change the
history table. Restore the backup or correct the unapplied migration, rehearse on
a new copy, and only then retry the release.
