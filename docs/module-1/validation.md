# MODULE 1 — Public Product Catalog validation

Source commit: `c7c022e33f100937cd0f072c3666fd0e26754d8e` (read only).
Date: 2026-09-06. Toolchain unchanged from Phase 0: Java21, Boot3.5.16, Maven3.9.16,
PostgreSQL17.6 Alpine, Testcontainers1.21.4. No external database or gateway was contacted.

## Source evidence and scope

Read product controller, repository, service, seven catalog entities, handlers and type factory;
Angular ProductService, home and product-detail consumers, and the existing API inventory.
Only search/random/detail are implemented. No product administration/audit, user/auth, cart/order,
payment or notification business code was added.

`tools/capture-catalog-oracle.cjs` loads original TypeScript through source ts-node in memory;
it does not execute compiled dist, AppModule, main, user bootstrap, or source `.env`. It connects
only to a disposable database `aims_oracle` at localhost:55433, with a random environment password.
Seven source catalog entities create its isolated schema; the synthetic shared seed fixture runs
there, then original ProductService produces the checked-in JSON fixtures. The container was
removed after capture. Source TypeORM package is0.3.29; no dependency install/build ran in source.

The diagnostic `typeorm-schema.sql` records DDL from the actual TypeORM schema builder before
initialization. Flyway V2 was authored explicitly from this evidence, preserving names/types/
precision/nullability/defaults, seven PKs, barcode uniqueness, price/stock checks, six CASCADE FKs
and generated PK/UQ indexes. No extra indexes or constraints were invented. CD track's nullable
product_id is intentionally retained. V1 remains byte-for-byte unchanged.

## Automated checks

Commands ran in `src/backend`, with the same temporary JDK/cache paths as Phase0:

```sh
export JAVA_HOME=/tmp/aims-java21/jdk-21.0.12.1+1/Contents/Home
export MAVEN_USER_HOME=/tmp/aims-maven-home
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=CatalogIntegrationTest,ProductServiceTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

| Check | Result |
| --- | --- |
| Module suite | 25 tests; 0 failures/errors/skips; BUILD SUCCESS |
| Full backend suite | 30 tests; 0 failures/errors/skips; BUILD SUCCESS |
| Maven verify | 30 tests; 0 failures/errors/skips; executable JAR built |
| Source JSON compatibility | Six full detail responses and default search match original TypeORM JSON exactly, including nulls, extra aliases, numeric-string prices and date formatting |
| Search repository | ILIKE subtype/media/track fields, DISTINCT for multiple matching tracks, parameter binding/injection input, wildcard behavior |
| Query rules | Category aliases, inclusive prices, CSV types, ACTIVE default, ALL excluding deleted, explicit DELETED, inverse price bounds, JavaScript number parsing |
| Random | Exactly20 from larger catalog, distinct IDs, ACTIVE only including zero stock, ignores unsupported limit query |
| Errors | Nest envelopes for malformed query/id, missing/deleted ID and unsupported subtype |
| HTTP boundary | Catalog GET public even with stale Bearer; admin/mutation routes denied; CORS preflight204/allowed/disallowed origin behavior, no credentials, no-store and no ETag |
| Schema comparison | Original DDL replayed into separate transactional schema; information_schema columns, pg_constraint names/definitions and pg_indexes equal Flyway catalog |
| DB constraints | Price/quantity/unique/FK violations rejected; source-permitted nullable track FK accepted; cascading product/media/CD/track deletion checked in rolled-back test |
| Foundation | All five tests retained; context/JPA validation/health/closed noncatalog routes/Flyway validation and re-run passed |
| Frontend/source | 70 file SHA-256 verification passed; source backend/frontend Git diff empty; existing source changes preserved |

The foundation test changes reflect authorized V2 tables and public routes, not a workaround for
a failed test. No tests were skipped or expectations altered to conceal an implementation error.
All code/database tests use PostgreSQL, no H2. Local raw logs: `/tmp/aims-module1-tests.log`,
`/tmp/aims-module1-suite.log`, `/tmp/aims-module1-build.log`, `/tmp/aims-module1-runtime.log`.

## Packaged application and upgrade

Started existing Compose volume (initial Flyway history contained only successfulV1), then the
packaged JAR under Java21 at port3000. V2 applied successfully and Hibernate validated all entities.
Health returnedUP; search/random returned200 empty arrays with localhost:4200 allow-origin and
Cache-Control no-store. Absent detail returned404 and NaN minPrice returned400 with Nest envelopes.
Final DB history contained successfulV1 andV2. No runtime product seeds were installed. Java and
AIMS PostgreSQL were stopped after smoke tests; the local volume was preserved.

## Remaining limits

- Schema parity is against original source metadata in PostgreSQL, not against production drift.
- JSON fixtures execute original repository/service serialization, not a full legacy AppModule
  deployment. Java HTTP behavior is checked separately with MockMvc and actual packaged HTTP.
- Catalog CORS is feature-scoped for this checkpoint. MODULE3 will integrate remaining CORS and JWT;
  MODULE5 will open product admin routes. No frontend behavior was changed to accommodate Java.
- Source CD-track ordering is unspecified, and explicit deleted-status searches remain allowed.
- Frontend build/end-to-end checkout remains scheduled for MODULE13.

Next: MODULE2 — User Domain and Roles, only after explicit user confirmation.
