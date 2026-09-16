# Deployment and environment

This module supplies deployable artifacts and validates them locally/through CI. The authorized
workspace runtime contacts the migrated Supabase schema; CI does not receive its credentials. This
does not deploy to Render, change DNS or enable live payment endpoints.

## Profiles and artifact

- `container`: bind0.0.0.0 and explicit external AIMS_DB_* values.
- `production`: container configuration plus required DB settings, explicit HTTPS APP_PUBLIC_URL,
  no VietQR sandbox-trigger flag, graceful shutdown20s, INFO logs and generic error responses.
  Default Compose selects this profile. Health exposes status only.
- No implicit/default Spring profile supplies a database. Running outside Compose requires an
  explicit profile and external database settings.

Run `./mvnw -B verify` with Java21 before release. The multi-stage Dockerfile then runs the Maven
Wrapper in its own JDK21 builder, packages with tests skipped, and copies only the executable JAR to
the runtime stage; the host does not need Java or Maven. Build with `docker build -t
aims-backend:<revision> src/backend` from root. The build context excludes tests, target, .env and
Maven cache. Base runtime JRE21 Alpine manifest is digest-pinned; UID10001:10001.
Run with read-only root, writable temporary /tmp, dropped capabilities and no-new-privileges as in
Compose. Set PORT for platform binding; health path `/actuator/health`. Terminate gracefully with
at least30s allowance. Retain previous image revision for application rollback.

`.env.supabase` remains mode 0600 and Git-ignored. `docker compose up --build` builds and starts only
backend/frontend, loads database/provider settings without copying them into Compose, forces the
production profile, `AIMS_DB_SCHEMA=aims_java`, and disables the VietQR test callback. There is no
local PostgreSQL service, port, volume, password generator or local Spring profile. The frontend
multi-stage image builds with digest-pinned Node24 and serves only `dist/frontend/browser` from
digest-pinned unprivileged Nginx on localhost4200. Both containers use read-only roots, ephemeral
`/tmp`, dropped capabilities and health checks.

The runtime connects directly to the same Supabase database as NestJS but deliberately does not
attach Spring to legacy `public`: that schema has no Flyway history and parallel NestJS/Spring
writers are unsafe. Rollback means stopping this stack or deploying the previous verified revision;
it no longer means switching to a local persistent database.

## Environment variables

Inject secrets from the deployment platform/secret store at runtime; never use image build args,
committed env files or CLI command literals containing credentials. Spring does not load .env itself.

| Variable | Meaning/default |
| --- | --- |
| SPRING_PROFILES_ACTIVE | production for deployment; default Compose explicitly uses production |
| PORT | 3000; server binds all interfaces in container/production |
| AIMS_DB_URL | Required JDBC PostgreSQL URL; Supabase transaction pooler also needs `prepareThreshold=0`; external DB should use certificate verification where supported |
| AIMS_DB_USERNAME / AIMS_DB_PASSWORD | Required container/production credentials |
| AIMS_DB_SCHEMA | PostgreSQL schema, default `public`; use `aims_java` for the isolated legacy snapshot |
| JWT_SECRET | Required, at least32 UTF-8 bytes; random key, no fallback; rotating invalidates user/merchant tokens |
| APP_PUBLIC_URL | Frontend base URL, localhost4200 locally; explicit HTTPS required production; controls payment redirects/email links |
| ALLOWED_ORIGINS | Comma-separated extra CORS origins; source localhost/Vercel rules remain unchanged |
| PAYPAL_API_BASE_URL | Default https://api-m.sandbox.paypal.com; explicitly select approved live endpoint when activating live payments |
| PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET | Provider credentials; missing disables usable PayPal operations |
| VIETQR_API_BASE_URL | Default https://dev.vietqr.org; use provider-approved production endpoint when activating |
| VIETQR_USERNAME / VIETQR_PASSWORD | Outbound gateway credentials |
| VIETQR_BANK_CODE / VIETQR_BANK_ACCOUNT / VIETQR_BANK_ACCOUNT_NAME | Merchant bank identity, mandatory for QR creation/validation |
| VIETQR_MERCHANT_USERNAME / VIETQR_MERCHANT_PASSWORD | Separate inbound Basic credentials for `/vqr/api/token_generate`; resulting merchant bearer protects callbacks |
| VIETQR_PAYMENT_TTL_MINUTES | Default15 |
| VIETQR_ENABLE_TEST_CALLBACK | Defaultfalse; production rejects true |
| NOTIFICATIONS_ENABLED | Defaultfalse; enabling starts SendGrid delivery, including queued backlog |
| SENDGRID_API_KEY / SENDGRID_FROM_EMAIL | Required for enabled provider; use verified sender |
| SENDGRID_FROM_NAME | AIMS Store |
| NOTIFICATION_POLL_MS | 5000; one ready notification per worker poll |
| JAVA_TOOL_OPTIONS | Optional JVM memory/runtime settings; do not put application secrets here (JVM prints this variable) |
| TZ | Set consistently for manager date filters, audit-day quotas and email dates; preserve intended server timezone |

Gateway endpoints still default to sandbox in production until explicitly configured; no live test
has been performed. Merchant callback URL: `/vqr/bank/api/transaction-callback` (sync alias retained).
Order/payment field spelling, token headers, PM authorization and decimal/date contracts are unchanged.

## Database transition and rollback

Flyway V1–V10, ddl-auto=validate, clean disabled, auto-baseline disabled. A fresh DB is tested from
zero. Existing NestJS databases have no verified production dump and may contain different metadata.
**Do not simply point this app at an existing nonempty database and turn on auto-baseline.**

Before any production cutover: obtain authorization and a backup; restore to an isolated rehearsal
DB; compare actual tables/constraints/indexes against recorded TypeORM metadata; reconcile legacy
money/payment state and data; design/review the baseline/import plan for that exact schema; rehearse
migration and application startup. No automatic baseline number is prescribed without that evidence.
Stop writes during final data transfer and gateway callback switch; verify balances, stock and pending
payment/refund journals. Reconcile unknown provider outcomes before retrying financial actions.

No default ADMIN is created or password reset on startup. Preserve imported authorized accounts.
For a fresh deployment, a trusted operator provisions the first ADMIN through a reviewed one-time
procedure: generate BCrypt with the configured PasswordEncoder using an interactively supplied
password, insert the user and users_roles ADMIN link transactionally, then verify login and audit.
Do not paste real passwords/hashes into Git or terminal command history. Admin API can then create
other users. This checkpoint does not create a real administrative account.

Rollback uses the previous verified image with compatible schema; never automatically drop tables,
reverse stock changes, delete journals or restore a stale backup while new payments are arriving.
Notify/reconcile providers as part of a separately approved operational rollback.

## Frontend and operational limits

Angular application source/hash is preserved including approved token adaptations. The authorized
container-only overlay replaces the old development image with a production static image and Nginx
SPA fallback; it does not change routes, requests, authentication or API selection. On localhost the
existing application configuration calls the Compose backend at localhost3000. On any non-localhost
hostname, production `API_BASE_URL` is still the existing Render hostname; a cutover there requires
access/approval to that service. A new backend hostname requires a separately approved minimal
frontend configuration change. TLS termination and cache policy remain deployment-platform duties.

No live provider authentication, sender verification, TLS termination or production traffic has been
checked. CORS retains broad legacy Vercel allowance. Notification outbox carries PII
and order capability: restrict DB/backups and set retention; retries can duplicate after remote
acceptance/local commit failure. Late bank transfers, ambiguous payments and FAILED emails need
operator reconciliation. See the detailed risk register and Module9–12 validation documents.

Official references: [GitHub Maven CI](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven)
and [Angular/Node compatibility](https://angular.dev/reference/versions). CI uses Node24 for Angular21.
