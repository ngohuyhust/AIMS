# AIMS migration status

## Current checkpoint

- Authorized scope: **MODULE 10 — VietQR**, after MODULE9 completion per `làm nốt module 9 và sang module 10 luôn đi`.
- Status: **MODULE 10 validated; commit/push in progress**.
- Next checkpoint after MODULE10: **MODULE 11 — Order Management and Refunds**, not authorized.
- User approved the proposed VietQR protection policy with `tiếp đi`; MODULE11 still requires confirmation.
- Business implementation: catalog, user/auth/admin, product administration/audit, stateless cart/shipping and transactional order placement/ownership/delivery and shared payment core, protected PayPal and VietQR.
- Current branch: `main`, following the user's explicit history consolidation and branch deletion.

## MODULE 10 result

- V8 exact legacy VietQR table plus unique bank receipts; RestClient QR generation, protected HTTP
  create/status/callbacks and isolated merchant token issuance. Test callback PM-only, default off.
- Exact order/account/amount/content proof, concurrent reuse/callback idempotency, atomic payment/
  order/receipt changes and expiry; late/ambiguous transfers require reconciliation.
- Module18/18 tests including98 source DTO fixtures; Angular7/7 and production build pass.
- Full suite626/626 and Maven verify626/626 pass with zero failures/errors/skips; executable JAR built. Push verification in progress. [Validation](docs/module-10-validation.md).

## MODULE 9 result

- Completion `4fbe6572b23a94674a690ef1c3505a826e6dd1d5` pushed and exact remote hash verified; tree was clean before MODULE10.

- PayPal OAuth/create/capture/refund via RestClient; token-owned create/capture, PM-only refund.
- V7 exact legacy PayPal table plus durable request/result journal. Verified money/order binding,
  concurrent retry idempotency and atomic shared/payment/order updates; pending results return409.
- Module17/17 tests including39 source DTO fixtures; frontend6/6 tests passed.
- Full suite608/608 and Maven verify608/608 passed with zero failures/errors/skips; executable JAR and Angular production build pass. Implementation `e3be40d005e34981065d15bfd5d8a1ebf187be46` pushed to origin/main; exact remote hash verified and tree clean before this completion record. [Validation](docs/module-9-validation.md).

## MODULE 8 result

- Completion `1094df3ff2acfccfa89a768c38b12ab34d0dbe96` verified before MODULE9.

- V6 shared PaymentTransaction only; exact source metadata, no PayPal/VietQR schema or endpoints.
- Separate gateway modality interfaces; pending attempt reuse, conditional state transitions,
  atomic order/payment confirmation and after-commit application event.
- Explicit user choice: freeze delivery when pending/paid; validate whole-VND order amount.
- Module17/17 tests cover idempotency, concurrent callbacks/create/expiry/delivery, rollback,
  schema parity and V5→V6 upgrade. Full suite591/591 and Maven verify591/591 passed;
  zero failures/errors/skips, executable JAR built.
- Implementation `9fdf90fcb60494ad7c2191b66697dcf346b6cea8` pushed to origin/main;
  git ls-remote verified the exact hash. Completion record is a separate documentation commit;
  its final hash and remote/clean-tree verification are reported in the checkpoint response.
- Frontend unchanged from MODULE7 approved hashes; original ISD baseline unchanged.
- [Validation](docs/module-8-validation.md); later provider/event-delivery limits documented.

## MODULE 7 result

- Completion `0a92754143af94407ecfa0544e23d9ccbc8cddba` verified on origin/main before MODULE8.
- V5 orders/order_items/delivery_info/invoices, full persisted response graph; no payment schema.
- Sorted pessimistic stock locks; atomic reservation/placement/invoice; order locks for delivery edits.
- Explicit user approval: token ownership protection plus minimal frontend edits. PM read-only JWT
  access retains manager UI; no capability disclosure in manager response. No public delivery edits.
- Shared BigDecimal/HALF_UP; source state/fee/DTO behavior documented with security changes.
- Module14/14 tests including54 DTO fixtures; full suite574/574; frontend5/5 and production build pass.
- Source70/70 untouched; frontend67 original files unchanged,3 edited originals and2 added files
  checked against explicit approved hashes. Original source baseline unchanged.
- Maven verify574/574 and executable JAR build pass, zero failures/errors/skips.
- Implementation `76251119040b528d3f98ed3b2efca58cd341b977` pushed to origin/main;
  git ls-remote verified the exact hash. Completion record is a separate documentation commit;
  its final hash and remote/clean-tree verification are reported in the checkpoint response.
- [Validation](docs/module-7-validation.md).

## MODULE 6 result

- Two public POST201 routes: cart/check-stock and shipping-fee under /api/orders.
- First-seen duplicate merging, stock issues, weight-only/default and volumetric/alternative,
  normalized province tariffs, strict discount threshold, decimal subtotal/VAT/total.
- User explicitly approved BigDecimal + HALF_UP (0.35 subtotal -> 0.04 VAT, fixing source0.03).
- No Flyway change or order/cart schema; no frontend edits, authentication, session or stock writes.
- 482 module tests passed, including472 original shipping cases and44 DTO fixtures.
- Full suite560/560 and Maven verify560/560 passed; zero failures/errors/skips; executable JAR built.
- Frontend70/70 unchanged; source HEAD/status preserved.
- Implementation `c8353fb37f873908282ae11904405a18e26cc5b0` pushed to origin/main;
  git ls-remote verified the exact hash. Completion record is a separate documentation commit;
  its final hash and remote/clean-tree verification are reported in the checkpoint response.
- [Validation](docs/module-6-validation.md).

## MODULE 5 result

- Completion commit `5dff092e25b79348a5b2cb0d38d79ad75a87fdf7` verified on origin/main before starting MODULE6.
- Six PRODUCT_MANAGER routes; four subtype writes; stock locking; batch deactivate/delete; audit.
- Explicitly approved JWT-email quota identity and transaction locking; x-manager-id still required.
- V4 product_logs matches original TypeORM metadata. Existing V1–V3 unchanged; no order tables/stubs.
- Fourteen module tests include 11 source DTO fixtures, PostgreSQL schema parity and concurrent
  stock/quota tests. Full suite78/78; frontend70/70 unchanged; source read-only.
- Maven verify78/78 and executable JAR build passed; zero failures/errors/skips in final runs.
- Implementation `8a5112b62f25b2c5eaeb067e9efed3e4f82d5d05` pushed to origin/main;
  git ls-remote verified the exact hash. Completion record is a separate documentation commit;
  its final hash and remote/clean-tree verification are reported in the checkpoint response.
- [Detailed validation](docs/module-5-validation.md); compatibility limits in contract/risks.

## MODULE 4 result

- Eight ADMIN-only routes, profile/status/roles/create, two reset contracts and audit listing.
- Signed JWT email attribution, transactional audit writes; response DTO maps never expose hashes.
- No Flyway change or default account seed. Frontend70/70 unchanged; source read-only.
- Module11/11, full suite64/64, Maven verify64/64; zero failures/errors/skips. JAR built.
- Implementation `fbfdcf10c3920337ce2de30cff04a41b012206d5` successfully pushed to origin/main.
  Completion record is a separate documentation commit; the final hash and remote verification
  result are reported in the checkpoint response.
- [Validation](docs/module-4-validation.md), contract and risks record deliberate safe deviations.

## MODULE 3 result

- Login/change-password POST201 and source-compatible successful payloads/error messages.
- HS25624h JWT; mandatory environment key, exact-role method security, BCrypt cost10 compatibility.
- Password update and audit are atomic; global source CORS/no-store, no frontend edits.
- Module13/13; full suite53/53; Maven verify53/53; zero final failures/errors/skips.
- Flyway V1–V3 unchanged; no new schema or default accounts. Admin reset-password deferred MODULE4.
- Frontend70/70 unchanged; source read-only. [Validation](docs/module-3-validation.md).
- Implementation `23b871d1dcd0508929f09f381326c1d9234db21a` pushed to origin/main;
  git ls-remote verified the exact hash. Completion record is a separate documentation commit;
  its final hash and remote check are reported in the checkpoint response.

## MODULE 2 result

- User, Role and UserAuditLog JPA entities; users_roles composite membership relation.
- Flyway V3 retains original types/defaults/nullability, named PK/UQ/FK/indexes and delete/update
  actions. V1 and V2 remain unchanged. Three roles seeded once; no default account bootstrap.
- Three repositories and transactional read-only UserDomainService lookups by ID/exact email.
  Internal entity JSON excludes passwordHash; no new HTTP routes, JWT or administration APIs.
- PostgreSQL integration tests cover metadata schema equality, constraints, multi-role persistence,
  audit retention, timestamps, credential exclusion, V2→V3 upgrade and safe repeated migration.
- Module tests10/10; full suite40/40; Maven verify40/40. Zero failures/errors/skips in final runs.
- Frontend70/70 unchanged; V1/V2 unchanged; original source changes preserved.
- [Detailed validation](docs/module-2/validation.md), [contract](docs/api-contract.md),
  [risks](docs/migration-risks.md).
- Source remains read-only at `c7c022e33f100937cd0f072c3666fd0e26754d8e`.
- Implementation commit: `12b3e2523ebb24b8fa906ffc2a536437efc84a74`
  (`feat(user): migrate user and role domain with safe role seeds`).
- Pushed to `origin/main`; git ls-remote verified the exact implementation hash. Working tree was
  clean before this completion record. This status-only record is committed/pushed separately;
  its own hash and final remote verification are reported in the user checkpoint.

## Git consolidation history

Before MODULE2, the user requested retaining all original commits on main and deleting other
branches. Main `4a695590bd4d2b3e735813c006dc487fdff6df05` retains all238 ISD commits and MODULE1
code. Remote and local now use only main; historical branch references below describe past work.

## MODULE 1 result

- Seven JPA entities and Flyway V2 for products/media/books/cds/cd_tracks/dvds/newspapers.
- Public GET search/random/detail; parameterized PostgreSQL queries, source-compatible JSON,
  scoped CORS/no-store and Nest error responses. Other module routes remain closed.
- Actual source TypeORM fixtures captured in a disposable isolated PostgreSQL database; source
  AppModule/main/user bootstrap and source .env were never executed/loaded.
- Module tests25/25; full suite30/30; Maven verify30/30. No failures/errors/skips.
- Existing local Compose volume upgraded V1→V2; packaged HTTP smoke checks passed at port3000.
- Frontend70/70 SHA-256 unchanged; source backend/frontend unchanged.
- [Detailed validation](docs/module-1/validation.md), [contract](docs/api-contract.md),
  [risk updates](docs/migration-risks.md).
- Implementation commit: `a7927ef83a2d55160f5ec4eee382ba51f5f9beef`
  (`feat(product): migrate public catalog with API compatibility tests`).
- Push succeeded to `origin/migration/spring-boot`; git ls-remote confirmed that exact hash and
  working tree was clean. This status-only completion record is pushed separately; its own hash
  and final remote/clean-tree checks are reported in the user checkpoint.
- Remote main was observed at `493f1c03c1ea745094ef2bbc9646f2b9c3ee1d33`, an external merge of
  Phase0 PR#1. No merge/rebase/main push was performed in this checkpoint. The migration branch
  retains Phase0 ancestry and contains only the authorized MODULE1 additions.
- Initial push review was rejected; original explicit per-module push authorization and destination
  were rechecked, and the same push was approved on review and succeeded. No workaround was used.

## PHASE 0 history

Completed at `98ce109ca726804e5849c492457070326d59ac28`; its foundation implementation and
validation are retained below as historical evidence.

## PHASE 0 provenance and Git

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

## PHASE 0 foundation scope

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

## PHASE 0 validation and checkpoint commits

Executed module test, full suite and Maven verify: each passed 5 tests, 0 failures/errors/skips.
Packaged JAR returned HTTP200 `{"status":"UP"}` at localhost:3000 with local Compose PostgreSQL.
Flyway V1 applied and repeat migration executed0; no business tables. Frontend SHA-256 and source
preservation checks passed. See [phase-0-validation.md](docs/phase-0-validation.md) for commands,
toolchain checksums and evidence. No Java or frontend content changed after passing these checks;
the final commit only records the verified checkpoint state.

## Remaining sequence (all require separate authorization)

| Checkpoint | Scope | Status |
| --- | --- | --- |
| MODULE 1 | Public product base/subtypes, Flyway V2, search/random/detail, repository/service/MockMvc tests | Complete |
| MODULE 2 | User/role/join/audit domain, safe role seeds | Complete |
| MODULE 3 | Login/JWT/password change/role security/CORS | Complete |
| MODULE 4 | User administration and audit APIs | Complete |
| MODULE 5 | Product administration/audit/manager quota | Complete |
| MODULE 6 | Cart/duplicate merging/shipping/VAT boundaries | Complete |
| MODULE 7 | Transactional placement/stock locks/customer token/delivery/detail | Complete |
| MODULE 8 | Payment domain/abstractions/states/idempotency/events | Complete |
| MODULE 9 | PayPal OAuth/create/capture/refund/redirect/currency/mock HTTP | Complete |
| MODULE 10 | VietQR QR/expiry/callback/auth/idempotency/sandbox/JSONB | Validated; push in progress |
| MODULE 11 | Order management/states/refunds/concurrency | Not started |
| MODULE 12 | Application events/SendGrid/provider isolation | Not started |
| MODULE 13 | Full compatibility/integration/Docker image/CI/production/frontend build | Not started |

## Risks awaiting later checkpoint decisions

See [migration-risks.md](docs/migration-risks.md). Remaining limits: actual deployed schema metadata,
durable event delivery, late/ambiguous bank-transfer reconciliation, order/refund lifecycle in
MODULE11 and the hardcoded frontend production API host. MODULE9/10 now protect payment endpoints;
merchant callback bearer authentication and exact bank/order/amount checks are implemented.

STOP after the MODULE10 checkpoint report. Do not start MODULE11 until explicit user confirmation.
