# MODULE 5 — Product Administration and Audit

Date: 2026-09-07. Read-only source c7c022e33f100937cd0f072c3666fd0e26754d8e.
Read product controller/service/repository, all create/update/stock/batch DTOs, validator factory,
four subtype handlers, audit entity, Angular ProductService and manager-header interceptor.
No source application/bootstrap/.env ran. No external database or provider was contacted.

## Authorized decision and scope

User explicitly selected signed JWT email + transactional quota locking while retaining required
x-manager-id. Six admin routes; four subtype writes; stock; batch status transitions; JSONB audit;
daily per-manager20 quota. No order schema/stubs or MODULE6 behavior. Existing order_items is checked
when available; its absence before MODULE7 denotes no persisted order reference.

V4 is authored from actual original TypeORM metadata via `tools/capture-product-log-schema.cjs`.
The independent generated DDL is replayed into an isolated schema in PostgreSQL, then compared
against Flyway columns/types/defaults/nullability, named constraints and indexes. All agree.
`tools/capture-product-admin-validation.cjs` executes original Nest ValidationPipe offline; 11
fixtures compare validation arrays and sanitized DTOs, covering create/update/nested/null cases.

## Checks

From src/backend using JAVA_HOME=/tmp/aims-java21/jdk-21.0.12.1+1/Contents/Home,
MAVEN_USER_HOME=/tmp/aims-maven-home:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=ProductAdminInputTest,ProductAdminIntegrationTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

- Module14/14; full suite78/78; final build counts recorded in MIGRATION_STATUS.md.
- Zero final test failures/errors/skips. PostgreSQL17.6 Testcontainers; Hibernate validates V4.
- Four subtype create/read, media aliases/numeric strings, partial CD edit vs track replacement,
  type/original-price immutability, inclusive price bounds, DTO validation and whitelist.
- JWT401, ADMIN-without-manager403, missing manager-header400, signed identity vs spoofed header.
- Two concurrent decrements from stock1: one200/one400, final0, one stock log.
- Two concurrent batch requests at quota19: one201/one400 despite different headers; final20.
- Quota separates actors/days and excludes missing IDs; batch count/duplicates/results/statuses.
- Ordered-product handling with test-only order_items fixture; no order table outside that test.
- JSONB audit fields/reason, nested product price strings, retained audit/null product on deletion.
- Failed subtype/audit insertion rolls back product/media/subtype/log writes.
- Exact original audit schema and 11 DTO oracle cases passed. Existing catalog JSON/schema tests
  retained. Foundation expects V4/table set and auth401 on newly opened admin routes. MODULE2's
  dedicated V2→V3 test now explicitly targets V3 so future migrations do not change its scope.
- Frontend70/70 SHA-256 unchanged; git diff --check passed; V1–V3 and source preserved.

Initial compile iterations fixed a compound var declaration and generic assertion ambiguity; final
runs passed without skips or weakened assertions. Logs /tmp/aims-module5-{tests,suite,build}.log.

## Limits

Schema parity is against source metadata, not production drift. Runtime validation uses disposable
PostgreSQL/Spring test contexts; existing Compose volume has not been upgraded here. Local startup
applies V4. Source permissive date/nested-null/compound-error edge cases are documented separately;
full lifecycle/order integration remains future scope. No frontend build or checkout E2E claim.
