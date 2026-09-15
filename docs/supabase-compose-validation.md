# Connected Supabase Compose validation

## Scope and boundary

The user authorized Spring Boot to connect to the Supabase database used by NestJS and to load the
existing provider configuration. Source remains read-only and secrets remain in `.env.supabase`.
Spring uses the already migrated `aims_java` schema rather than unsafe auto-baselining of legacy
`public`; the isolated local Compose stack remains available as rollback.

## Evidence

- The NestJS source uses direct PostgreSQL/TypeORM, not a Supabase REST API, and has
  `synchronize: true`; it was not started.
- A session with `default_transaction_read_only=on` copied1.521 rows from all19 `public` business
  tables into an ephemeral PostgreSQL17.6/Flyway V1–V10 clone. Per-table counts matched, quality
  checks passed and the production-profile Spring image became healthy.
- The connected Compose topology contains only backend/frontend. Both became healthy; backend runs
  UID10001:10001 with read-only root, ephemeral `/tmp`, all capabilities dropped and
  `no-new-privileges`.
- Backend reported production profile and `aims_java`; database/JWT/PayPal/VietQR/SendGrid variables
  were present without values being displayed. VietQR test callback was false.
- Backend actuator returned `UP`, the public catalog endpoint returned182 ACTIVE products, and
  frontend health returned `ok`.
- No PayPal, VietQR or SendGrid request was made. No database/provider secret was written to Git.
- Angular tests pass7/7 and its production build passes on Node24.16.0. Maven `verify` passes663/663
  with zero failures/errors/skips on Java21 and PostgreSQL17.6 Testcontainers; executable JAR built.
- Default Compose verifier, Supabase Compose verifier, source-ref frontend hash verification and
  `git diff --check` pass.

Final suite results and commit hashes are recorded in `MIGRATION_STATUS.md` and the checkpoint
report.
