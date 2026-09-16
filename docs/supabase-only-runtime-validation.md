# Supabase-only runtime validation

## Authorized scope

The user explicitly requested removal of the local application database path to reduce runtime
configuration. Default Compose now uses the existing Git-ignored `.env.supabase` and migrated
`aims_java` schema. This does not authorize rewriting legacy `public`, changing API contracts or
removing PostgreSQL Testcontainers.

## Removed runtime paths

- The persistent PostgreSQL Compose service, localhost55432 port and named volume.
- `application-local.yml` and the implicit `local` Spring profile.
- `tools/init-local-env.py` and its generated local DB-password workflow.
- The duplicate `compose.supabase.yml` and duplicate verifier; its two-service topology is now the
  sole/default `docker-compose.yml` and `tools/verify-compose.py` contract.

The ignored obsolete `.env` local-secret file and exact stopped `aims-local` containers/volume were
removed from this workspace. Images are retained because Spring builds and mandatory tests use them.

## Preserved safeguards

- Compose forces production, `AIMS_DB_SCHEMA=aims_java`, and a disabled VietQR test callback.
- Secrets stay in mode0600 `.env.supabase`; CI substitutes an empty temporary env only to validate
  topology and never contacts Supabase.
- Backend/frontend keep localhost-only ports, health checks, non-root users, read-only filesystems,
  ephemeral `/tmp`, dropped capabilities and `no-new-privileges`.
- Flyway V1–V10, `ddl-auto=validate`, no clean and no auto-baseline remain unchanged.
- Testcontainers and image smoke tests retain disposable PostgreSQL17.6 for deterministic validation;
  they create no persistent application database.

Final runtime checks, test counts and commit hashes are recorded in `MIGRATION_STATUS.md` and the
checkpoint report. Local verification completed with:

- `docker compose up -d --build --wait`: backend/frontend healthy using the default file.
- Backend actuator `UP`, catalog182 ACTIVE products, frontend health `ok`; backend UID10001:10001,
  read-only, all capabilities dropped and `no-new-privileges`.
- No `aims-local` containers/volume and no obsolete `.env` file remain in the workspace.
- `npm test -- --watch=false`: 7/7 pass; `npm run build`: pass on Node24.16.0.
- Java21 `./mvnw -B verify`: 663/663 pass, zero failures/errors/skips; Flyway V1–V10 on disposable
  PostgreSQL17.6 Testcontainers and executable JAR pass.
- `python3 tools/verify-compose.py`, source-ref frontend hash verification and
  `git diff --check`: pass.
