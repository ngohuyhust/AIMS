# Legacy Supabase connection and migration

The source `.env` contains a direct PostgreSQL Supabase connection, not a Supabase REST API key.
The migration tools never start NestJS (`synchronize: true`) and never print credentials.

On 2026-09-08 the source was queried with `default_transaction_read_only=on`. PostgreSQL 17.6 had
19 public business tables and no Flyway history. Constraints and indexes match the corresponding
AIMS tables; column differences are physical order only. A disposable rehearsal copied all 1,521
rows with exact per-table counts into Flyway V1-V10 and the production-profile backend became healthy.
The archive used mode 0600 and was deleted; database/container/network were removed.

Quality aggregates: no negative stock, invalid catalog price, orphan order item/payment or non-BCrypt
user. There are 27 historical orders without customer capability tokens. These remain inaccessible to
customer-token routes; PRODUCT_MANAGER access still works. Historical states include pending payment
and refund records, which must be reconciled before a live gateway cutover.

`tools/migrate-legacy-supabase.py` creates `aims_java` in the same Supabase database, runs all Flyway
migrations there and copies a repeatable-read snapshot from `public`. It never changes `public`, never
deletes an existing target schema and refuses to overwrite a nonempty divergent target. It compares
every row using named columns, checks per-table counts, starts the Java image against `aims_java`, and
leaves the new schema in place. Run the read-only rehearsal first:

```sh
docker build -t aims-backend:local src/backend
python3 tools/rehearse-legacy-data.py
python3 tools/migrate-legacy-supabase.py
```

For Java use `AIMS_DB_SCHEMA=aims_java` with the same host/database credentials mapped to AIMS_DB_*.
The Supabase transaction pooler requires `prepareThreshold=0` in the JDBC URL, matching the legacy
TypeORM `maxPreparedStatements: 0`; otherwise repeated startups can fail with prepared statement
name collisions.
Keep notifications off and gateway callbacks unchanged until traffic is deliberately switched.
Because the copy is a point-in-time snapshot, later writes in `public` are not synchronized. A final
cutover therefore requires a write freeze or a reviewed delta transfer, followed by count/payment/
stock reconciliation. Do not run NestJS and Java as independent writers after the snapshot.

This workspace has a Git-ignored `.env.supabase` with mode 0600 for local execution. It uses a stable
local JWT secret and maps the legacy PayPal sandbox, VietQR development and SendGrid configuration to
the Spring variable names. `NOTIFICATIONS_ENABLED=false` and `VIETQR_ENABLE_TEST_CALLBACK=false`, so
startup does not send email or enable the synthetic callback. No provider transaction was made during
validation. The running container name is `aims-supabase-api`; it binds only `127.0.0.1:3000`, runs as
UID 10001 with a read-only root, and can be stopped/restarted with `docker stop` / `docker start`
without changing `public`.

Migration completed on 2026-09-08: `aims_java` has Flyway V1-V10 and an exactly compared snapshot
of 1,521 rows across all 19 legacy tables. A production-profile Java container passed health and
catalog checks against that schema. The legacy `public` table set and row counts remained unchanged.
Implementation `ca41956b93ae6fff2c9ef0478ff742c7a965d27e` is verified on `origin/main`;
[CI run 34251292984](https://github.com/ngohuyhust/AIMS/actions/runs/34251292984) passed both jobs.

The schema can be abandoned without affecting NestJS by stopping Java and leaving traffic on
`public`. Dropping `aims_java` is intentionally not automated and needs a separate backup/approval.
