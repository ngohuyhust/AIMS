# MODULE 11 validation — order management/refunds

Source c7c022e33f100937cd0f072c3666fd0e26754d8e read only. Read original order/customer controllers,
query/refund/lifecycle services, repository/transition table and Angular order/payment services.
User chose only PM cancellation for paid orders. No frontend edits; existing token query and PM JWT.

V9 adds durable order action reservation only; V1–V8 unchanged. PM list/search/date/payment filters,
approval, reject/cancel and manual VietQR refund; customer unpaid token cancellation. Stock restoration
is sorted/atomic/once; PayPal journal composes with durable cancellation to recover local failures.
Order approval, payment creation, cancellation and delivery share guards under the order lock.
Source unpaid approval remains allowed; pending payment blocks lifecycle changes, paid customer
cancellation denied403. Manual refund records an external transfer, never executes one.

Java21, Maven Wrapper/local cache, PostgreSQL17.6-alpine Testcontainers:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=OrderLifecycleIntegrationTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

Module14/14: duplicate/concurrent cancel/reject, stock exactly once, customer ownership/paid denial,
manual refund/duplicate/rollback, pending-payment blockade, source unpaid approval/invalid states,
role protection, pagination/search/date/payment/refund filters, concurrent PayPal refund once,
stock failure after remote refund/retry, direct refund after approval denial, cancellation blocks
new payment, and preserving/repeatable V8→V9 upgrade. Mock HTTP only; no real PayPal calls.
Previous closed-endpoint tests now assert401 for newly protected PM routes; future routes remain403.
Full suite640/640 and Maven verify640/640 passed with zero failures/errors/skips; executable JAR built. No skipped tests or H2 substitution.

Frontend hash verifier passes source70/70 and all approved overlay hashes; no new frontend changes.
Source HEAD/status retained (.DS_Store/src/.DS_Store modified and ArchitecturalDesign/ActivityDiagram/
untracked). No secrets/artifacts committed. Logs /tmp/aims-module11-{tests,suite,build}.log.

Implementation `bca83c3503555f7bafd12273dcd26676fd03d527` pushed to origin/main; exact hash and clean tree verified. Completion record is a separate documentation commit.
