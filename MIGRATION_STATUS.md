# AIMS migration status

## Current checkpoint

- Authorized scope: **MODULE 3 — Authentication and Security**, authorized by `tiếp module 3`.
- Status: **MODULE 3 validated, pushed and verified; awaiting user confirmation**.
- Next checkpoint: **MODULE 4 — User Administration**, not authorized yet.
- Required next message: `TIẾP TỤC MODULE 4`.
- Business implementation: public catalog, user domain and login/JWT/change-password security.
- Current branch: `main`, following the user's explicit history consolidation and branch deletion.

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
| MODULE 3 | Login/JWT/password change/role security/CORS | Complete; awaiting user confirmation |
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

STOP after the MODULE 3 checkpoint report. Do not start MODULE4 until the user's explicit confirmation.
