# AIMS migration status

## Current checkpoint

- Authorized scope: **PHASE 0 — Repository and foundation only**.
- Status: **PHASE 0 implementation validated, pushed and verified; awaiting user confirmation**.
- Next checkpoint: **MODULE 1 — Public Product Catalog**, not authorized yet.
- Required next message: `TIẾP TỤC MODULE 1`.
- Business implementation: none (Product/User/Auth/Order/Payment/Notification are not implemented).

## Provenance and Git

- Read-only source: `/Users/abc/Documents/Study/ITSS/ISD.20252-25`.
- Source commit: `c7c022e33f100937cd0f072c3666fd0e26754d8e`.
- Initial source status: modified `.DS_Store`, `src/.DS_Store`; untracked
  `ArchitecturalDesign/ActivityDiagram/`. These pre-existing changes are preserved.
- Destination: `/Users/abc/Documents/Study/ITSS/AIMS`.
- Origin: `https://github.com/ngohuyhust/AIMS.git`.
- Remote was nonempty (README only), main commit `b6e4edfbfa53a6e47aa81664cc9d48f6fca8a0e7`.
- Work branch: `migration/spring-boot`; existing main history retained.
- Implementation commit: `57b30586c8dbea063eafe4359f94e78178caf91c`
  (`chore: initialize Spring Boot migration workspace`).
- Push: successful to `origin/migration/spring-boot`; `git ls-remote` confirmed that exact hash.
- Remote main remains `b6e4edfbfa53a6e47aa81664cc9d48f6fca8a0e7`.
- Working tree was clean after the implementation push and before this status-only update.
- This final status record is a separate documentation commit, pushed on the same branch.
  Its own hash and final clean-tree/remote equality are reported in the user checkpoint, because
  a commit cannot store its own hash. A final push failure must still be reported as incomplete.

## Scope delivered for validation

- 70 tracked Angular source/config/asset files copied byte-for-byte; SHA-256 manifest and verifier.
- Java 21 Maven project, Spring Boot 3.5.16, Wrapper 3.3.4 / Maven 3.9.16.
- MVC, Validation, Data JPA, Security, Actuator, PostgreSQL/Flyway and JUnit/Mockito/Testcontainers.
- Local profile at port 3000, loopback only; isolated PostgreSQL Compose DB at port 55432.
- Generated ignored local password; no source `.env` or credentials copied.
- `/actuator/health`, no health details; all remaining paths denied at this scaffold checkpoint.
- V1 Flyway connectivity migration (`SELECT 1`), no business tables, ddl-auto=validate,
  clean disabled, auto-baseline disabled.
- Five foundation integration tests; no Docker-unavailable skip or external provider calls.
- AGENTS.md, README, 44-route API inventory, risk register and frontend manifest.

## Validation and checkpoint commits

Executed module test, full suite and Maven verify: each passed 5 tests, 0 failures/errors/skips.
Packaged JAR returned HTTP200 `{"status":"UP"}` at localhost:3000 with local Compose PostgreSQL.
Flyway V1 applied and repeat migration executed0; no business tables. Frontend SHA-256 and source
preservation checks passed. See [phase-0-validation.md](docs/phase-0-validation.md) for commands,
toolchain checksums and evidence. No Java or frontend content changed after passing these checks;
the final commit only records the verified checkpoint state.

## Remaining sequence (all require separate authorization)

| Checkpoint | Scope | Status |
| --- | --- | --- |
| MODULE 1 | Public product base/subtypes, Flyway V2+, search/random/detail, repository/service/MockMvc tests | Not started |
| MODULE 2 | User/role/join/audit domain, safe role seeds | Not started |
| MODULE 3 | Login/JWT/password change/role security/CORS | Not started |
| MODULE 4 | User administration and audit APIs | Not started |
| MODULE 5 | Product administration/audit/manager quota | Not started |
| MODULE 6 | Cart/duplicate merging/shipping/VAT boundaries | Not started |
| MODULE 7 | Transactional placement/stock locks/customer token/delivery/detail | Not started |
| MODULE 8 | Payment domain/abstractions/states/idempotency/events | Not started |
| MODULE 9 | PayPal OAuth/create/capture/refund/redirect/currency/mock HTTP | Not started |
| MODULE 10 | VietQR QR/expiry/callback/auth/idempotency/sandbox/JSONB | Not started |
| MODULE 11 | Order management/states/refunds/concurrency | Not started |
| MODULE 12 | Application events/SendGrid/provider isolation | Not started |
| MODULE 13 | Full compatibility/integration/Docker image/CI/production/frontend build | Not started |

## Risks awaiting later checkpoint decisions

See [migration-risks.md](docs/migration-risks.md). Key unresolved items: actual deployed schema
metadata; public order data/access tokens and delivery edits; unguarded payment/refund routes;
VietQR amount/signature checks; race conditions; manager header attribution; user hash disclosure;
hardcoded frontend production API host. None has been silently implemented or changed in Phase 0.

STOP after the Phase 0 checkpoint report. Do not edit further until the user's explicit confirmation.
