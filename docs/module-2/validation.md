# MODULE 2 — User Domain and Roles

Date: 2026-09-07. Source: `c7c022e33f100937cd0f072c3666fd0e26754d8e`, read only.
Java21, Spring Boot3.5.16, PostgreSQL17.6 Alpine, existing Maven/Testcontainers dependencies.

## Evidence and implementation

Read source user/role/audit entities, repository, UserModule bootstrap, UserAdminService/controller,
AuthService and Angular UserService, plus the existing API inventory. No source bootstrap ran.
`node tools/capture-user-schema.cjs` loads only the original entity metadata and renders SQL using
the installed TypeORM0.3.29 PostgreSQL query runner without connecting to a database. The generated
fixture is `src/backend/src/test/resources/user/typeorm-schema.sql`.

Flyway V3 was authored from that evidence; a PostgreSQL test replays the independent TypeORM SQL
in a separate schema and compares columns/defaults/nullability/precision, constraint definitions
and names, and indexes. Junction user FK CASCADE/CASCADE and role FK NO ACTION/NO ACTION are exact.
Audit user FK is nullable SET NULL. Four user tables and three shared role seeds are the entire
database addition. V1/V2 were not changed; no additional enum/check constraints were invented.

Three entities, three repositories and read-only transactional UserDomainService support ID and
case-sensitive email lookup with roles initialized. JPA callbacks maintain Instant timestamps on
entity persistence/update. Internal passwordHash is explicitly excluded from Jackson serialization.
Authentication/password hashing/administration HTTP business logic remains for MODULE3/4.

## Validation

From `src/backend`, with JAVA_HOME `/tmp/aims-java21/jdk-21.0.12.1+1/Contents/Home`
and MAVEN_USER_HOME `/tmp/aims-maven-home`:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=UserDomainIntegrationTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

- Module suite: 10 tests, zero failures/errors/skips.
- Full suite: 40 tests, zero failures/errors/skips (all30 existing tests retained).
- Maven verify: 40 tests, zero failures/errors/skips; executable JAR built.
- Context loaded and Hibernate validated on real PostgreSQL Testcontainers.
- V2→V3 upgrade preserved a catalog row; repeat migration preserved a deactivated synthetic
  account's credential and STAFF membership. Role seeds did not create any default user.
- Multi-role round trip, null phone, ACTIVE default, creation/update timestamps, exact-email and
  missing-user lookups, password exclusion, duplicate email/role/membership constraints,
  referenced-role deletion rejection, user cascade and retained null-user audit all passed.
- Existing catalog schema comparison now explicitly targets its seven tables, so added authorized
  user tables do not invalidate that independent test. Foundation expects V3 and all11 domain tables.
- `python3 tools/verify-frontend.py`: 70/70 SHA-256 matches; no added frontend source files.
- `git diff --check`: passed. Original source status preserved, no backend/frontend edits there.

Initial module execution failed because Docker Desktop was stopped. Docker was started and the
complete suite rerun successfully; no Docker-unavailable skip or test weakening was introduced.
Logs: `/tmp/aims-module2-tests.log`, `/tmp/aims-module2-suite.log`, `/tmp/aims-module2-build.log`.

## Limits and next checkpoint

Schema parity is against source metadata, not production drift. No external DB/service was contacted.
Upgrade/runtime validation here uses disposable PostgreSQL and Spring integration contexts; the
existing Compose volume was not upgraded in this checkpoint. Starting the app locally applies V3.
No new user HTTP parity claim is made. Legacy password-hash disclosure on user creation remains R10
for MODULE4; future HTTP DTOs need explicit field/date/null compatibility tests. MODULE3 is next and
requires separate user authorization. Work remains on main per the user's single-branch decision.
