# AIMS migration status

## Current checkpoint

- Authorized scope: **remove the concrete dead/redundant files, dependencies and frontend debug
  output identified by the user-requested clean-code audit, without changing contracts**.
- Status: **implementation, documentation, verification and Git publication complete**.
- Next checkpoint: live `public`→`aims_java` delta/cutover, Java25/toolchain migration and provider
  production activation remain separate checkpoints requiring explicit authorization.
- Current branch: `main`, preserving consolidated ISD history.

## CODE AND REPOSITORY CLEANUP result

- Removed redundant direct declarations of `spring-boot-starter-security` and `flyway-core`;
  OAuth2 Resource Server and the PostgreSQL Flyway module retain the same transitive runtime
  components. The full Java suite proves Spring Security, Flyway V1–V10 and packaging still work.
- Excluded SendGrid's legacy `commons-logging` transitively because Spring uses its compatible
  `spring-jcl` bridge; this removes the duplicate-facade warning without changing SendGrid behavior.
- Removed four orphaned PayPal/VietQR capture scripts that were not referenced by code, CI or
  validation documentation. Historical migration evidence and active rehearsal tools remain.
- Removed the Angular root's unused signal, startup log and empty stylesheet. Removed product-admin
  debug logs, including the line that printed the JWT from local storage. UI, routes, requests,
  responses and token handling are unchanged.
- Extended the frontend integrity verifier to record explicit approved deletions. The immutable
  source baseline still verifies 70/70 files; the target now reports 62 unchanged, seven approved
  edits, three approved additions and one approved deletion.
- Angular tests pass 7/7 and production build passes. Java 21/Maven 3.9.16 `verify` passes 663/663
  with zero failures/errors/skips using PostgreSQL 17.6 Testcontainers; executable JAR builds.
  Compose, source-ref frontend and diff checks pass. Implementation
  `f82e67cec146caab316ace0e711d1f142c66f48c` was pushed to `origin/main`; its exact remote hash and
  clean working tree were verified before this completion record. The final status hash is reported
  in the user checkpoint.
- [Validation](docs/code-cleanup-validation.md).

## SUPABASE-ONLY RUNTIME SIMPLIFICATION result

- Promoted the two-service Supabase topology to default `docker-compose.yml`. The normal command is
  now `docker compose up --build`; it starts only Spring production and Angular/Nginx against the
  Git-ignored `.env.supabase` and `aims_java` schema.
- Removed the persistent PostgreSQL Compose service, localhost55432 port, named volume, generated
  local database password workflow, `application-local.yml`, implicit local Spring profile, duplicate
  `compose.supabase.yml` and duplicate verifier. The obsolete ignored `.env`, exact three stopped
  `aims-local` containers and `aims-local_aims-postgres-data` volume were deleted from this workspace.
- PostgreSQL remains only in mandatory disposable Testcontainers, image smoke and migration
  rehearsal tooling. These isolated resources test Flyway/schema behavior and are automatically
  removed; they are not an alternative application runtime or persistent local database.
- Actual default `docker compose up -d --build --wait` rebuilt Spring with the simplified resources
  and made backend/frontend healthy. Actuator returns `UP`, catalog returns 182 ACTIVE products,
  frontend health returns `ok`, and backend remains UID 10001/read-only with capabilities dropped.
- No HTTP/JSON/authentication, Angular, database schema or Flyway change. Secrets remain outside Git;
  a fresh clone requires an operator-provided `.env.supabase` with no fallback credentials.
- Angular tests pass 7/7 and production build passes. Java 21 Maven `verify` passes 663/663 with zero
  failures/errors/skips using PostgreSQL 17.6 Testcontainers; executable JAR builds. Compose and
  source-ref frontend verifiers plus diff checks pass. Implementation
  `b4ff249dad657c4b3ec6916779acd1585ac23481`; exact remote verification follows the completion push.
- [Validation](docs/supabase-only-runtime-validation.md).

## CONNECTED SUPABASE COMPOSE result

- Added `compose.supabase.yml`: it builds/runs only Spring backend and Angular/Nginx, reads the
  existing mode0600 Git-ignored `.env.supabase`, forces production with `AIMS_DB_SCHEMA=aims_java`,
  and forces the VietQR test callback off. Default isolated Compose remains the rollback path.
- NestJS uses PostgreSQL/TypeORM rather than Supabase REST. Spring connects directly to that same
  Supabase database through the transaction pooler, but not the unsafe legacy `public` schema:
  `public` has no Flyway history and repository policy forbids auto-baselining/parallel writers.
- Fresh external rehearsal was forced read-only and copied1.521 rows/19 tables from `public` to a
  disposable PostgreSQL17.6 Flyway V1–V10 clone. All counts matched; catalog/relational/password
  quality checks and production-profile health passed; all temporary resources were removed.
- Actual Supabase Compose backend/frontend are healthy. Backend is UID10001/read-only/hardened,
  actuator returns `UP`, catalog returns182 ACTIVE products, and frontend health returns `ok`.
  DB/JWT/PayPal/VietQR/SendGrid variables were presence-checked without displaying values.
- Provider configuration is loaded as authorized, but validation made no provider request.
  PayPal/VietQR remain sandbox/development, test callback is false, while enabled SendGrid can send
  real email for newly generated eligible events.
- No Java/Angular/API/schema/Flyway change and no secret committed. CI validates this topology with
  a temporary empty environment file. Implementation
  `1d2158b23017d705b7402e61e64065c103374cbc`; exact remote verification follows the completion push.
- [Validation](docs/supabase-compose-validation.md).

## FRONTEND COMPOSE RUNTIME result

- Replaced the legacy Angular development Dockerfile with a digest-pinned multi-stage build:
  Node24.16.0 runs reproducible `npm ci` and the production build; unprivileged Nginx receives only
  `dist/frontend/browser`, exposes `/health` and supports Angular SPA fallback.
- Default `docker compose up --build` now builds/starts PostgreSQL, Spring backend and frontend.
  The frontend binds localhost4200, waits for backend health, runs UID101:101 with a read-only root,
  ephemeral `/tmp`, all capabilities dropped and `no-new-privileges`.
- Actual `docker compose up -d --build --wait` made all three services healthy. `/`, `/login`, FE
  `/health` and backend actuator health passed; a second frontend build reused every build layer.
- Angular tests pass7/7 and production build passes on Node24.16.0. Maven `verify` passes663/663 with
  zero failures/errors/skips on Java21 and PostgreSQL17.6 Testcontainers; executable JAR built.
  Compose and frontend/source-hash verifiers pass, including the read-only source-ref comparison.
- No Angular application/API behavior, Java, schema or Flyway change. The three frontend container
  files are recorded as an explicit approved overlay. `npm ci` reports33 dependency advisories
  (3 low,11 moderate,18 high,1 critical); dependency upgrades are not silently included because
  they would change the preserved frontend and require a separate reviewed checkpoint.
- Already-pushed commits remain intact; repository policy prohibits deleting/replacing them or
  force-pushing. Implementation `69009a8df0c23847706d6bcb260095ae6102a29a`; exact remote
  verification is reported after the completion commit is pushed.
- [Validation](docs/frontend-compose-validation.md).

## DEFAULT COMPOSE SELF-BUILD result

- Backend Dockerfile is a digest-pinned multi-stage build: official Maven3.9.16/Temurin21 builder
  runs `./mvnw`, BuildKit caches dependencies, and the existing digest-pinned JRE21 runtime receives
  only the executable JAR. Host JDK/Maven and a prebuilt `target` directory are no longer required.
- Removed the optional `application` profile, so default Compose includes both PostgreSQL and backend.
  After the one-time secure `python3 tools/init-local-env.py`, the normal command is
  `docker compose up --build` (or `-d --wait`). No fixed database/JWT credential was introduced.
- Tests-first Compose verifier initially failed because only PostgreSQL was a default service, then
  passed after the change. Actual `docker compose up -d --build --wait` built the JAR in Docker and
  made both services healthy; health returned `UP`, catalog returned an empty list, and runtime
  remained UID10001/read-only. A second image build used all relevant cache layers.
- Full Maven `verify` passes663/663 with zero failures/errors/skips on Java21 and PostgreSQL17.6
  Testcontainers; executable JAR built. Frontend verification passes all70 source files and approved
  overlays. No schema/Flyway, application API, Angular or read-only source change.
- Implementation `0059ad542d724825d65dcdf177d100c2ebed102e`; exact remote verification is
  reported in the checkpoint response after this completion record is pushed.
- [Validation](docs/compose-self-build-validation.md).

## SPRING SECURITY REQUEST-BOUNDARY REFACTOR result

- Replaced the custom JWT servlet filter with Spring Boot OAuth2 Resource Server,
  `BearerTokenAuthenticationFilter`, Spring `JwtDecoder`, `JwtAuthenticationConverter` and
  `@AuthenticationPrincipal Jwt`. Application and VietQR merchant token signing now use Spring
  `JwtEncoder`; direct application Nimbus signing/verifying calls were removed.
- Product/user/order management, PayPal refunds and the optional VietQR test trigger now consume the
  Spring authentication principal/authorities and use request or method authorization instead of
  reparsing the manager token in business services. Login remains the standard
  `AuthenticationManager`/`DaoAuthenticationProvider` chain completed in the prior checkpoint.
- Preserved exact URLs, headers, JSON, JWT claims/lifetimes, 401/403 behavior, public stale-token
  tolerance, order capabilities and merchant-token isolation. No schema/Flyway or Angular change.
- Authentication/JWT tests pass15/15, affected-module tests pass80/80, and full `test` plus Maven
  `verify` each pass663/663 with zero failures, errors or skips on Java21 and PostgreSQL17.6
  Testcontainers; executable JAR built. Frontend verification passes all70 source files and approved
  overlay hashes. Read-only source remains at `c7c022e33f100937cd0f072c3666fd0e26754d8e`.
- No test contacted PayPal, VietQR, SendGrid, Supabase or another production service. No JDK was
  downloaded; the existing Java21 container image was used. Git publication is recorded after the
  implementation and completion commits are pushed.
- Implementation `f2c07a8f7b5ec4beb22d9f278d856d06fc31a885`; exact remote verification is
  reported in the checkpoint response after this completion record is pushed.
- [Validation](docs/spring-security-boundary-validation.md).

## SPRING SECURITY AUTHENTICATION REFACTOR result

- Replaced manual login user lookup/status/password checks with `AuthenticationManager`,
  `DaoAuthenticationProvider`, `AimsUserDetailsService` and the existing bcryptjs-compatible
  `LegacyBcryptPasswordEncoder`. Successful Spring credentials and principal hashes are erased.
- Preserved POST `/api/auth/login` status, JSON, JWT claims/lifetime, role authorities and exact
  wrong-credentials/disabled-account messages. Database failures remain500; JWT request filtering,
  change-password behavior, sessions/form login and token revocation are unchanged.
- Authentication integration tests pass11/11. Full `test` and `verify` each pass662/662 with zero
  failures, errors or skips on Java21 and PostgreSQL17.6 Testcontainers; executable JAR built.
  Java21 Maven Enforcer passes using the existing `eclipse-temurin:21-jdk` Docker image.
- No Flyway migration or database schema change. Frontend verification passes all70 source files
  and approved overlay hashes. No external payment/email/database service was contacted.
- Implementation `03846c8fd373f9c3d1c7f418189d91188963c9c2`; exact remote verification is reported in the
  checkpoint response after this completion record is pushed.
- Read-only source remains at `c7c022e33f100937cd0f072c3666fd0e26754d8e` with its pre-existing
  `.DS_Store` changes and untracked activity-diagram directory preserved.
- [Validation](docs/authentication-spring-refactor-validation.md).

## CONVENTIONAL SPRING PACKAGE REFACTOR result

- Refactored all backend features into direct `controller`, `service`, `repository`, `entity` and
  `dto` packages, with explicit `exception`, `event`, `security`, `client`, `gateway` and `provider`
  packages where the class role requires one.
- Updated package declarations, imports and the explicit accessors needed at package boundaries.
  HTTP routes, JSON contracts, validation, transaction boundaries, database schema and Flyway
  migrations are unchanged. Clean Java 21 production and test compilation pass.
- Maven `verify` passes 661/661 tests with zero failures, errors or skips and builds the executable
  JAR. Frontend verification passes all 70 source files against the approved baseline.
- The superseded full-backend refactor and its status commit are replaced on `origin/main`; history
  through `a74f5d7f0c7702742f375e33311b59d40f35ca74` remains intact.
- Implementation `4eccce06d3c53abdbbefe12df621a08aef217f2d` is recorded by this completion commit;
  exact remote verification is reported in the checkpoint response.
- Read-only source remains at `c7c022e33f100937cd0f072c3666fd0e26754d8e` with its pre-existing
  `.DS_Store` changes and untracked activity-diagram directory preserved.
- [Package convention and final layout](docs/backend-structure.md).

## Legacy Supabase result

- Direct PostgreSQL endpoint inspected with read-only transaction; NestJS was not started because
  TypeORM uses `synchronize: true`. Legacy `public` has 19 tables and no Flyway history.
- Constraints/indexes match AIMS; only physical column order differs. Disposable rehearsal copied
  all 1,521 rows, preserved exact per-table counts and booted the production Java profile.
- Created isolated `aims_java` in the same Supabase database, applied Flyway V1-V10, copied and
  compared all 19 tables in one repeatable-read transaction. `public` was not modified.
- Java container on localhost:3000 is healthy and catalog returns 182 active products from
  `aims_java`; non-root/read-only runtime and VietQR gateway test disabled.
- Supabase pooler uses JDBC `prepareThreshold=0`, equivalent to the source `maxPreparedStatements: 0`,
  preventing intermittent server prepared-statement name collisions.
- No negative stock/invalid prices/orphan order items or payments/non-BCrypt users. Twenty-seven
  historical orders lack customer tokens; pending/refund states need reconciliation before cutover.
- Local `.env.supabase` is ignored and mode 0600. It maps the PayPal sandbox, VietQR development
  and SendGrid variables from the legacy backend. Real SendGrid delivery was explicitly enabled on
  2026-09-09 after confirming zero pending outbox messages; its API key authenticated successfully.
  The VietQR test callback remains disabled.
- Java 21 Maven verify passed 661/661; the idempotent migration recheck compared all 1,521 rows,
  and the provider-configured local container returned health `UP`.
- Implementation `ca41956b93ae6fff2c9ef0478ff742c7a965d27e` pushed to `origin/main`; exact
  remote hash verified. [GitHub CI 34251292984](https://github.com/ngohuyhust/AIMS/actions/runs/34251292984)
  passed backend and frontend. Supabase pooler/provider follow-up
  `50da457ff696fc7e446e1fddf829c0e2e273afca` is also pushed and verified by
  [GitHub CI 34253734702](https://github.com/ngohuyhust/AIMS/actions/runs/34253734702).
- [Procedure and limits](docs/legacy-supabase-migration.md).

## MODULE 13 result

- Production/container profiles, runtime Docker image, optional Compose backend, GitHub CI,
  integration journeys, environment/deployment/rollback documentation; no frontend changes.
- Module5/5, full suite661/661 and Maven verify661/661 pass without failures/errors/skips.
- Angular7/7 and production build pass; Docker production smoke and Compose configuration pass.
- Source and frontend hashes verified from both read-only sibling and preserved Git history.
- Implementation `3e6c45ea2696daf4cfcda4e450a43aa54b1ce1e8` pushed to origin/main; exact remote hash and clean tree verified.
- [GitHub CI run34244665470](https://github.com/ngohuyhust/AIMS/actions/runs/34244665470): backend and frontend SUCCESS, including image smoke on Linux.
- This separate completion record contains no code changes; its final hash/push are reported in the user checkpoint.
- [Validation](docs/module-13-validation.md), [deployment runbook](docs/deployment.md).

## MODULE 12 result

- Completion `48bea5c167d476efdd7ee9fa83a9f39230ae1179` verified on origin/main before MODULE13.

- Four order email events, transactional V10 outbox, SendGrid SDK, escaped templates and isolated
  bounded retries. Delivery defaults off; no real email sent and no frontend edits.
- Module16/16, full suite656/656 and Maven verify656/656 pass with zero failures/errors/skips; executable JAR built. Frontend/source hashes unchanged.
- Implementation `646eec524acdd9eb8a5b8c26a2960194eaa20a8b` pushed to origin/main; exact remote hash and clean tree verified. This separate completion record is pushed and its hash reported in the user checkpoint.
- [Validation and configuration](docs/module-12-validation.md).

## MODULE 11 result

- Completion `749968a06f6c0d52e886feb84edfc14b855aab49` pushed and verified on origin/main before MODULE12.

- PM lists/approve/reject/cancel/manual VietQR refund; customer-owned unpaid cancellation only.
- V9 durable lifecycle action, sorted stock restore, PayPal refund recovery, atomic manual refund,
  pending payment/cancellation guards and after-commit lifecycle events. No frontend edits.
- Module14/14, full suite640/640 and Maven verify640/640 pass with zero failures/errors/skips; executable JAR built. Implementation `bca83c3503555f7bafd12273dcd26676fd03d527` pushed to origin/main; exact hash and clean tree verified.
- [Validation](docs/module-11-validation.md).

## MODULE 10 result

- Completion `c728df37102429b4645f0dd92151a1c766a80a51` verified on origin/main before MODULE11.

- V8 exact legacy VietQR table plus unique bank receipts; RestClient QR generation, protected HTTP
  create/status/callbacks and isolated merchant token issuance. Test callback PM-only, default off.
- Exact order/account/amount/content proof, concurrent reuse/callback idempotency, atomic payment/
  order/receipt changes and expiry; late/ambiguous transfers require reconciliation.
- Module18/18 tests including98 source DTO fixtures; Angular7/7 and production build pass.
- Full suite626/626 and Maven verify626/626 pass with zero failures/errors/skips; executable JAR built. Implementation `ce8710bb947f7699e02031a2668a74f5aa9f89a2` pushed to origin/main; exact remote hash and clean tree verified before this completion record. [Validation](docs/module-10-validation.md).

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
| MODULE 10 | VietQR QR/expiry/callback/auth/idempotency/sandbox/JSONB | Complete |
| MODULE 11 | Order management/states/refunds/concurrency | Complete |
| MODULE 12 | Application events/SendGrid/provider isolation | Complete |
| MODULE 13 | Full compatibility/integration/Docker image/CI/production/frontend build | Complete |

## Risks awaiting later checkpoint decisions

See [migration-risks.md](docs/migration-risks.md). Remaining limits: actual deployed schema metadata,
notification retention/delivery reconciliation, late/ambiguous bank-transfer reconciliation and
the hardcoded frontend production API host. MODULE9/10 now protect payment endpoints;
merchant callback bearer authentication and exact bank/order/amount checks are implemented.

Original PHASE0–MODULE13 sequence complete. STOP awaiting user direction; no production rollout authorized.
