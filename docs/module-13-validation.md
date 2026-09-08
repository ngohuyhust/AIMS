# MODULE 13 — integration and deployment validation

Read original Nest main/controller/gateway contracts, legacy Docker/Compose and Angular API_BASE_URL,
auth/order/payment consumers. Source c7c022e33f100937cd0f072c3666fd0e26754d8e remains read only.

## Implemented

- Production/container profiles with explicit DB variables, graceful shutdown and deployment guards.
- Digest-pinned Java21 runtime Docker image, non-root execution, healthcheck and minimal build context.
- Optional Compose application profile; existing local database/volume mapping retained.
- GitHub Actions: Java21 verify/Testcontainers/image smoke and Node24 npm ci/test/build/hash checks.
  No credentials, deployment step, real provider calls or image registry publishing.
- Frontend verifier can read the original ISD commit already preserved in AIMS Git history, so CI
  checks the source baseline and approved overlay without a second repository or extra token.
- README/environment/cutover/rollback documentation. No business schema migration or frontend edit.

## API coverage

The entire previous656-test suite remains intact: catalog JSON/schema oracles; user/roles/auth/admin;
product DTO/audit/quota; cart/shipping boundaries; placement/token/stock rollback; payment concurrency;
PayPal mock HTTP; VietQR DTO/schema/callback proof; order lifecycle/refunds; notifications/retries.
Those module-specific source fixtures and approved policy changes define compatibility; this is not
an assertion that unapproved unsafe legacy behaviors or an unknown live DB are reproduced.

New production-profile CheckoutJourneyTest performs actual controller/filter/service/repository
composition through MockMvc on fresh PostgreSQL and a local HTTP VietQR stub. It logs in a BCrypt PM,
searches catalog, checks stock, places an order, creates QR, issues merchant bearer, repeats bank
callback, checks owned detail, then covers approval or paid-customer denial/PM cancellation/manual
refund. It checks one stock restoration and notification counts, dispatching only mocked SendGrid.
A third journey covers repeat unpaid cancellation and Angular CORS/token headers.
ProductionConfigurationTest requires explicit DB/public URL and rejects insecure URL/test callback.

## Executed locally

Java21, Maven Wrapper and PostgreSQL17.6-alpine Testcontainers; Node24.16.0:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=CheckoutJourneyTest,ProductionConfigurationTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
npm test -- --watch=false
NG_BUILD_MAX_WORKERS=2 npm run build
python3 tools/verify-frontend.py
python3 tools/verify-frontend.py --source-ref
docker build -t aims-backend:local src/backend
python3 tools/smoke-container.py
```

Module5/5; full suite661/661 and verify661/661; zero failures/errors/skips. Angular7/7 and production
build pass. Image smoke passes on fresh disposable PostgreSQL with production profile, read-only
root, non-root user, all10 migrations, actual health/catalog HTTP and protected PM401 response.
Temporary containers/network removed by script; Compose configuration also validates using synthetic
environment variables and `/dev/null` env-file, without reading the project's ignored .env.
Frontend source70/70 and all approved hashes pass via both local source and preserved Git history.
Source pre-existing .DS_Store/src/.DS_Store changes and untracked ActivityDiagram remain unchanged.
Logs `/tmp/aims-module13-{tests,suite,build,image,smoke,frontend-tests,frontend-build}.log`.

## Deployment boundaries

No production deploy, database connection, DNS/Render switch, real gateway request or email occurred.
Existing production frontend API hostname remains; a new hostname needs explicit frontend approval.
Actual DB import/baseline, initial admin provisioning, live provider activation, TLS, reconciliation
and notification retention need an operational rollout. See [deployment runbook](deployment.md).
Remote CI result and commit/push verification are recorded after publishing the workflow.
