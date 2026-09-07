# MODULE 7 validation — Order Placement

Source read-only HEAD: `c7c022e33f100937cd0f072c3666fd0e26754d8e`.
User authorized MODULE7 and explicitly selected **token protection + minimal frontend changes**.
MODULE8 and payment/provider/cancellation behavior were not implemented.

## Scope and evidence

Read source order/customer controllers, OrderService placement/detail/delivery, repository and
four entities, cart/delivery/place DTOs, shipping rules, Angular order/payment services, consumers
and auth interceptor. Offline oracle tools import only DTO/entity metadata; no source bootstrap,
environment, database synchronization, credential reuse or external service access.

- V5 adds orders, order_items, delivery_info, invoices. JPA entities preserve nullable relations,
  decimal12,2, Instant timestamps, unique64-char capability, checks, FK actions and exact names.
  PostgreSQL compares columns/defaults/constraints/indexes against original TypeORM DDL in a
  separate schema. V4-to-V5 upgrade preserves existing products and repeat migration executes0.
- Placement merges duplicates, locks products in ascending ID order, validates ACTIVE/quantity,
  reserves stock and creates the complete graph in one transaction. It returns source-shaped
  decimal strings, dates, relation IDs/nulls and a fresh32-byte hex capability.
- Detail requires capability, or verified PRODUCT_MANAGER JWT for reads with capability omitted.
  Customer token route retained; delivery requires capability and locks order before repricing.
  Shared BigDecimal/HALF_UP follows the previous explicit decision. Payment lookup has no stub:
  null before MODULE8 table exists, real latest-SUCCESS query when present.
- Atomic rollback checked on invoice insert/update constraints, missing delivery and monetary
  overflow. Concurrency checked for last unit, opposite product order and competing delivery edits.
- Original54 DTO fixtures check validation/whitelist. Public exposure, wrong/cross-order tokens,
  manager read-only behavior, stale JWT coexistence, CORS/no-store and future-route denial tested.
- Framework payload/bind/error-row tracing disabled by default to avoid secret/PII logging.

## Commands and results

Java21 at `/tmp/aims-java21/jdk-21.0.12.1+1/Contents/Home`, Maven Wrapper with
`MAVEN_USER_HOME=/tmp/aims-maven-home`; PostgreSQL17.6-alpine Testcontainers and Docker Desktop.

From `src/backend`:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=OrderInputTest,OrderIntegrationTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

Final: module14/14, full suite574/574, Maven verify574/574; zero failures/errors/skips. Executable
JAR built. Logs `/tmp/aims-module7-tests.log`, `/tmp/aims-module7-suite.log`,
`/tmp/aims-module7-build.log`. Real PostgreSQL only, no H2 or unavailable-container skipping.

From `src/frontend`:

```sh
npm test -- --watch=false
NG_BUILD_MAX_WORKERS=2 npm run build
```

Final:5/5 tests; production bundle built, budgets passed. Logs `/tmp/aims-module7-frontend-tests.log`
and `/tmp/aims-module7-frontend-build.log`. Initial sandbox build aborted without diagnostics;
retry outside sandbox with2 workers succeeded. Existing Hello-title boilerplate test failed against
the already-router-only template; it now tests actual routed content through the app shell.
Three capability tests verify creation storage, per-order header isolation, reload/payment-service
sharing and remembering token links only after successful access. No tests removed/skipped.
Dependencies copied from source node_modules into ignored target node_modules; source remains read-only.

From root:

```sh
node tools/capture-order-schema.cjs
node tools/capture-order-validation.cjs
python3 tools/verify-frontend.py
git diff --check
git -C ../ISD.20252-25 rev-parse HEAD
git -C ../ISD.20252-25 status --short
```

Source70/70 matches original SHA-256 manifest. Target67 originals unchanged;3 original-file edits
(order/payment services and shell test),2 additions (capability service/test) match explicit approved
hashes. No UI, route, request-body, provider-method or API-host edits. V1–V4 unchanged. Source status
preserved: modified .DS_Store/src/.DS_Store and untracked ArchitecturalDesign/ActivityDiagram/.
Review found no committed credentials, environment files, caches, dist, node_modules or target.

Prior-module tests retain their assertions with authorized schema/route examples updated. Product
admin ordered-product test now uses real order_items rather than a temporary future-module stub.
The original frontend manifest is not rewritten; the verifier still checks every source file and
rejects target changes outside exact approved hashes/file set.

## Limits and next checkpoint

This is local migration validation, not production deployment. Production frontend host remains
legacy configuration. Same-tab capability storage supports reload/payment redirects; lost storage
requires the original token link. No capability expiry/revocation or orderId-only recovery added.
PENDING_PROCESSING delivery repricing and abandoned reservations need payment/lifecycle decisions
before those later features become active. MODULE8 must retest the real payment-method lookup.
Malformed nested DTO/email edge limits are in the API contract. No order cancellation or provider
calls were enabled here. Exact implementation/final status hashes and remote verification are
recorded in the subsequent completion commit and checkpoint response.
