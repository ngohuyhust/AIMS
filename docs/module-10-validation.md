# MODULE 10 validation — VietQR

Source c7c022e33f100937cd0f072c3666fd0e26754d8e read only. MODULE9 completion
4fbe6572b23a94674a690ef1c3505a826e6dd1d5 verified on origin/main before starting.
User authorized MODULE10 and then approved the proposed protection policy by asking to continue.

Read original customer/merchant controllers, DTOs, repository/entity, API client, adapter/tests,
shared payment orchestration and Angular payment service/component. Offline captures only; no Nest
bootstrap, source .env, external DB or real gateway/bank access. Source status and frontend baseline
preserved. See api-contract.md for all paths, statuses, payloads/config and deliberate corrections.

Implemented:
- V8 original VietQR table with exact columns/defaults/nullability/constraints and auxiliary bank
  receipt ledger; unique bank account+transaction ID and unique shared-payment FK. V1–V7 untouched.
- RestClient Basic/token/QR generation with source fields/aliases, safe error mapping and bounded
  HTTPS/loopback I/O. Persist pending intent before generation and serialize reuse on the order lock.
- HTTP create/status require order capability. Merchant Basic issues300s JWT with isolated signing
  key/issuer/audience; callback routes reject missing/invalid/expired/user tokens. Merchant aliases
  normalize at the boundary; original customer create/callback DTOs match98 source fixtures.
- Callbacks validate bank/account/order/exact amount/content/type/time/unique target. Receipt and
  PAID/shared SUCCESS/order transition commit together; duplicates do not repeat events. Expiry
  atomically changes QR/shared state; ambiguous/late transfers require reconciliation.
- Test trigger disabled by default, PM-only when enabled, exact sandbox host gate. No order lock
  while triggering a provider that might callback synchronously. Never directly fabricates PAID.
- Angular service only: create token header and payment-to-order mapping for polling headers. Same
  UI/paths/payloads. Reload goes through existing create/reuse flow before polling; no new storage.

Reproducible commands from src/backend, Java21 toolchain and Maven cache as MODULE9:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository '-Dtest=Vietqr*Test' test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

Module18/18 passed: 98 DTO fixtures, API auth/body/nested aliases, provider-error sanitization,
transport/sandbox-host validation, merchant expiry/key isolation/invalid credentials, protected
HTTP create/status/by-ref/callback aliases and duplicate callbacks, ownership denial, default-off
trigger, enabled PM gate without holding transaction, concurrent creation/confirmation, one-bank-
receipt/two-order rejection, wrong account/order/fractional amount/debit, atomic rollback, expiry/
late rejection, retry after generation failure, exact legacy schema and preserving V7→V8 upgrade.
Full suite626/626 and Maven verify626/626 passed, zero failures/errors/skips; executable JAR built. No skipped tests or H2 substitution.
PostgreSQL17.6-alpine Testcontainers and local HTTP mocks only.

Frontend npm test -- --watch=false passed7/7; NG_BUILD_MAX_WORKERS=2 npm run build passed. Source70/70
baseline hashes and target67 unchanged originals/3 approved edits/2 additions pass verifier. Only
payment.service.ts and related capability test changed this checkpoint; original manifest untouched.
Logs: /tmp/aims-module10-{tests,suite,build,frontend-tests,frontend-build}.log.

Source status retained .DS_Store/src/.DS_Store modifications and untracked ArchitecturalDesign/ActivityDiagram/.
No credentials, dist, target, node_modules or source edits committed. Lifecycle/refund/stock restoration
is MODULE11, notification durability MODULE12, real sandbox acceptance/deployment MODULE13. This
checkpoint does not claim live merchant acceptance or automatic reconciliation/refund of late transfers.
