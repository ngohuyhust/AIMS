# Deployment and environment

This module supplies deployable artifacts and validates them locally/through CI. It does not deploy
to Render, change DNS, contact a production database or enable real payments/email.

## Profiles and artifact

- `local` (default): localhost3000, isolated localhost55432 PostgreSQL. Only AIMS_LOCAL_DB_PASSWORD
  and JWT_SECRET needed. Never interprets legacy DB_HOST/DB_DATABASE variables.
- `container`: bind0.0.0.0 and explicit AIMS_DB_* values; Compose uses its own PostgreSQL service.
- `production`: container configuration plus required DB settings, explicit HTTPS APP_PUBLIC_URL,
  no VietQR sandbox-trigger flag, graceful shutdown20s, INFO logs and generic error responses.
  Choose one profile; do not combine local with production. Health exposes status only.

Build with Java21 `./mvnw -B verify` in src/backend, then `docker build -t aims-backend:<revision>
src/backend` from root. Runtime Dockerfile copies the verified executable JAR only, excludes source,
.env, Maven cache and tests. Base JRE21 Alpine manifest is digest-pinned; UID10001:10001.
Run with read-only root, writable temporary /tmp, dropped capabilities and no-new-privileges as in
Compose. Set PORT for platform binding; health path `/actuator/health`. Terminate gracefully with
at least30s allowance. Retain previous image revision for application rollback.

## Environment variables

Inject secrets from the deployment platform/secret store at runtime; never use image build args,
committed env files or CLI command literals containing credentials. Spring does not load .env itself.

| Variable | Meaning/default |
| --- | --- |
| SPRING_PROFILES_ACTIVE | production for deployment; Compose explicitly uses container |
| PORT | 3000; server binds all interfaces in container/production |
| AIMS_LOCAL_DB_PASSWORD | Generated local Compose password, local profile only |
| AIMS_DB_URL | Required container/production JDBC PostgreSQL URL; external DB should use `sslmode=verify-full` and trusted CA |
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

Frontend source/hash is preserved including approved token adaptations. Production API_BASE_URL
is still the existing Render hostname; a cutover there requires access/approval to that service.
A new backend hostname requires a separately approved minimal frontend configuration change.
The preserved frontend Dockerfile is the old development image; deployment should publish the
verified Angular `dist/frontend/browser` static output with SPA fallback to index.html.

No real production health, provider credentials, sender verification, TLS termination or external
schema has been checked. CORS retains broad legacy Vercel allowance. Notification outbox carries PII
and order capability: restrict DB/backups and set retention; retries can duplicate after remote
acceptance/local commit failure. Late bank transfers, ambiguous payments and FAILED emails need
operator reconciliation. See the detailed risk register and Module9–12 validation documents.

Official references: [GitHub Maven CI](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven)
and [Angular/Node compatibility](https://angular.dev/reference/versions). CI uses Node24 for Angular21.
