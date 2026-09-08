# MODULE 9 validation — PayPal

Source HEAD c7c022e33f100937cd0f072c3666fd0e26754d8e, read-only sibling ISD.20252-25.
Read original PayPal controller, three DTOs, adapter/repository/entity/API client, credit-card
interface, payment orchestration/tests and Angular payment service/component. No Nest bootstrap,
source .env, external DB or real payment API calls. User approved capability-protected create/capture,
PRODUCT_MANAGER-only refund and minimal frontend headers. MODULE10 separately authorized afterward.

Implementation: V7 legacy PayPal table and auxiliary durable operation journal; OAuth cache,
RestClient transport, same-request-ID retries, protected HTTP contracts, full resource verification,
GET polling of pending outcomes, atomic local apply/recovery and shared-core after-commit events.
See api-contract.md and migration-risks.md for intentional behavior changes and reconciliation limits.

Commands from src/backend, JAVA_HOME=/tmp/aims-java21/jdk-21.0.12.1+1/Contents/Home,
MAVEN_USER_HOME=/tmp/aims-maven-home, Docker Desktop PostgreSQL17.6-alpine:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository '-Dtest=Paypal*Test' test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

Module17/17 passes: original39 DTO fixtures/error ordering, decimal boundaries/invalid proofs,
transport configuration, OAuth Basic/form/cache/401 invalidation, create/capture/refund contracts,
ownership/role denial before I/O, concurrent create/capture/refund, stale replay rejection, pending
GET refresh, stable retry key, upstream-error sanitization, local capture/refund apply rollback and
journal recovery, exact legacy column/constraint schema comparison and V6→V7 preservation/repeat.
An assertion generic type ambiguity and duplicated legacy refund errors were corrected before the
final run. Previous regression tests now expect V7 and PayPal tables/routes, retain future VietQR
closure, and pin the historical payment upgrade to V6. No existing checks removed or skipped.

Frontend: npm test -- --watch=false passed6/6. Minimal create/capture token headers and test updated;
no UI/payload/URL changes. Original source70/70 and approved overlay hashes pass verify-frontend.py.
Full suite608/608 and Maven verify608/608 passed, zero failures/errors/skips; executable JAR and Angular production build passed. Initial Angular build approval was blocked by usage quota; after the user renewed the request, normal approval succeeded.
Logs: /tmp/aims-module9-{tests,suite,build,frontend-tests,frontend-build}.log.

Source status remains modified .DS_Store/src/.DS_Store and untracked ArchitecturalDesign/ActivityDiagram/.
Earlier Flyway migrations remain byte-for-byte unchanged. No credentials/artifacts are committed.
