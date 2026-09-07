# MODULE 8 validation — Payment Core

User authorized completion of MODULE7 and immediate continuation to MODULE8. MODULE7 completion
`0a92754143af94407ecfa0544e23d9ccbc8cddba` was pushed and verified before this work.
Read-only source HEAD remains `c7c022e33f100937cd0f072c3666fd0e26754d8e`.

## Source and scope

Read original PaymentService/PaymentRepository, PaymentTransaction, credit-card/QR/payment-service
interfaces, source PaymentService tests and notification event bus/type, order repository and
delivery update behavior. PayPal inverse entity metadata was read only to reconstruct the shared
table offline; no Java provider entity/stub or gateway implementation is introduced.

- V6 adds only payment_transactions, matching exact original column/default/precision/nullability,
  named amount check, PK, nullable order FK and CASCADE delete. Enum is Java-side varchar mapping;
  no extra PostgreSQL enum/check/index changes. Fresh schema and V5 upgrade tested on PostgreSQL.
- Internal begin/confirm/fail/markRefunded/find/latest methods own shared lifecycle. Whole VND is
  BigDecimal HALF_UP; creation and confirmation validate order/method/amount. Begin coalesces the
  sole matching pending attempt and rejects conflicting active attempts under the order lock.
- Confirmation locks order then transaction, conditionally marks SUCCESS and PENDING_PROCESSING
  together, and publishes ORDER_PAYMENT_SUCCEEDED after commit. Repeats return false; FAILED cannot
  revive; SUCCESS/REFUNDED repeats cannot reset an advanced order. Refund method records a verified
  result only, without a provider call, stock restoration or lifecycle orchestration.
- User explicitly approved freezing delivery while pending/paid and comparing payment amounts.
  Delivery now accepts PENDING only, with409 when an active PENDING/SUCCESS transaction exists.
  All paths share order locking; failed attempts allow delivery change and a newly matched amount.
- Protected order detail reads the real latest SUCCESS method. No missing-table fallback remains.
- Separate credit-card and QR gateway interfaces preserve modality differences. No concrete bean,
  credentials, SDK, controller, network call, email consumer, PayPal or VietQR table added.

## Reproducible checks

Java21 `/tmp/aims-java21/jdk-21.0.12.1+1/Contents/Home`, Maven Wrapper with
`MAVEN_USER_HOME=/tmp/aims-maven-home`; Docker Desktop / PostgreSQL17.6-alpine Testcontainers.

From `src/backend`:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=PaymentIntegrationTest,PaymentStatusTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

Final results: module17/17, full suite591/591, Maven verify591/591, zero failures/errors/skips;
executable JAR built. Logs `/tmp/aims-module8-tests.log`, `/tmp/aims-module8-suite.log`,
`/tmp/aims-module8-build.log`. A test helper name initially conflicted with MockMvc status();
it was renamed before all final runs. No tests weakened/removed/skipped and no H2 substitution.

Coverage: all16 state pairs, whole-VND half boundaries, pending coalescing/concurrent begin,
wrong proof order/method/amount, cancelled order rejection, duplicate/concurrent confirmation,
failure-versus-confirmation race, refund conditional updates, no event inside an uncommitted
outer transaction, rollback/no event, forced order-update failure rollback, real paymentMethod,
delivery freeze, failed-attempt repricing, competing delivery/begin using the old amount,
internal JSON decimals/IDs/dates, schema parity/nullability/cascade and V5-to-V6 preservation.
Future provider endpoints remain403 and provider tables absent.

From repository root:

```sh
node tools/capture-payment-schema.cjs
python3 tools/verify-frontend.py
git diff --check
git -C ../ISD.20252-25 rev-parse HEAD
git -C ../ISD.20252-25 status --short
```

Source70/70 matches its original SHA-256 baseline. No frontend edits in MODULE8; target remains
MODULE7's67 unchanged originals,3 approved edited originals and2 approved additions, all exact
hashes. V1–V5 unchanged. Source status remains modified .DS_Store/src/.DS_Store and untracked
ArchitecturalDesign/ActivityDiagram/. No source/environment/production DB/provider access.
Previous-module regression tests now expect V6/shared table, keep future gateway tables/routes
closed, and pin the historical V4→V5 upgrade test toV5. Concurrent delivery test uses the newly
approved editable PENDING state; separate payment tests verify paid/pending-payment denial.

## Integration limits

No public payment API exists yet. Future adapters must authenticate provider proof and verify
currency/order/amount before constructing PaymentConfirmation; use stable shared transaction IDs
for external idempotency. Provider row changes must join the same transaction in order/shared/
gateway lock order; provider repeats must still consult core state. Core guards do not reconcile
an external charge received after terminal failure or allow arbitrary method switching mid-attempt.

The after-commit event is in-process and not durable. A process crash or consumer failure can lose
notification delivery; it is not an exactly-once email/outbox claim. MODULE12 must define delivery
and reconciliation requirements. Provider/refund orchestration, expiry and order stock release are
deferred to their authorized modules. Exact implementation and final completion hashes/push
verification are recorded in the subsequent status commit and checkpoint response.
