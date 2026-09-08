# MODULE 12 — notifications

Source `c7c022e33f100937cd0f072c3666fd0e26754d8e`: notification service/event bus/provider
interface, SendGrid provider/templates/specs, payment/lifecycle publishers and Angular order URLs.
No source startup, environment loading, real email or external payment calls.

Four events: payment succeeded, approved, rejected, cancelled. Subjects/plain text, order/invoice/
payment/refund information, vi-VN money and escaped order capability links retain source semantics.
HTML is rendered in Java with the same information and visual structure (not byte-identical).
The paid-email Cancel button is omitted to follow the user's MODULE11 PM-only paid cancellation rule.
No HTTP routes or frontend changes. SendGrid uses the official Java SDK 4.10.3:
[SDK release](https://github.com/sendgrid/sendgrid-java/releases/tag/4.10.3).

V10 adds only `notification_outbox`; V1–V9 are unchanged. A synchronous internal event records the
order/delivery/invoice/payment snapshot in the business transaction; the previous after-commit events
remain available. A unique event/channel key suppresses duplicate callbacks; changing refund status
creates a new notification. Snapshot failure rolls back the business transaction; provider failure
cannot roll back a committed payment/order. Workers claim committed rows with FOR UPDATE SKIP LOCKED
and hold only the outbox row during bounded network I/O. No order lock is held while sending.

## Configuration and delivery limits

- `NOTIFICATIONS_ENABLED=false` by default: no scheduled delivery or provider calls. Events still queue.
- To enable in a reviewed environment, configure `SENDGRID_API_KEY`, `SENDGRID_FROM_EMAIL`, optional
  `SENDGRID_FROM_NAME` (default AIMS Store), `APP_PUBLIC_URL` (default http://localhost:4200), then
  `NOTIFICATIONS_ENABLED=true`. Public URLs require HTTPS; HTTP is accepted only for loopback.
- `NOTIFICATION_POLL_MS` defaults5000; each worker handles one ready row per poll. Missing key/sender
  keeps queued rows pending without consuming attempts. Enabling delivery also processes this backlog.
- SDK connects within3s; socket timeout10s; automatic HTTP retries/redirects disabled. HTTP429/5xx and
  transport failures retry with backoff; maximum6 attempts. Other provider4xx stop as FAILED.
- Missing recipient is SKIPPED. Each channel has a separate row/attempt state. Logs/error columns
  contain only sanitized codes; HTTP wire/header logging is off. Outbox JSON contains customer PII
  and the order capability, so it needs the same access restrictions/backups/retention as orders.
- Delivery is at-least-once within the retry policy, not exactly-once: process/database failure after
  SendGrid accepts a message but before SENT commits can cause duplicate email on retry. SENT means
  provider acceptance, not inbox delivery. FAILED needs operator investigation; no public retry API.
- Before requeueing a failed record, verify provider delivery externally and correct its cause; a
  manual retry may duplicate delivery. Retention/purge tooling and bounce webhooks are not implemented.

## Validation

Java21/Maven Wrapper with `/tmp/aims-maven-repository`; PostgreSQL17.6-alpine Testcontainers only.

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=NotificationIntegrationTest,EmailProviderTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
python3 tools/verify-frontend.py
```

Tests cover transactional snapshot/rollback, idempotency, concurrent workers, retries/permanent
failure/retry cap, missing configuration/recipient, provider isolation, V9→V10 preservation and
repeatability, four template variants/escaping/money/URLs, SDK request payload/error sanitization.
SendGrid is mocked; no real email. Module16/16, full suite656/656 and Maven verify656/656 pass; zero failures/errors/skips, executable JAR built.
Frontend SHA-256 verifier passes all original/approved hashes; source HEAD and pre-existing status
are unchanged. Logs: /tmp/aims-module12-{tests,suite,build}.log. Secret/artifact and diff checks pass.

Implementation `646eec524acdd9eb8a5b8c26a2960194eaa20a8b` pushed to origin/main; exact remote
hash and clean working tree verified. Final completion record is a separate documentation commit.
