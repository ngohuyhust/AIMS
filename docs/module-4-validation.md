# MODULE 4 — User Administration, 2026-09-07

Source read-only c7c022e33f100937cd0f072c3666fd0e26754d8e. Read UserAdminController,
UserAdminService, repository/entities, CreateUserDto, BcryptPasswordHasher, both Auth reset methods
and Angular UserService. Original class-validator was executed offline for validation fixtures;
no Nest bootstrap/.env was loaded. API/risk documents describe intentional safety deviations.

Implementation: eight ADMIN-only routes, transactional create/profile/status/roles/two resets,
explicit user/log responses without hashes, user list order and latest10 audit entries, global
audit retaining nullable user, signed email attribution. Flyway V1–V3 untouched; no new seed.

Commands in src/backend, existing Java21 and /tmp/aims-maven-home cache:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=UserAdminIntegrationTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

Coverage: JWT/ADMIN restrictions on reads and every mutation, create/list/global audit response
fields and credential exclusion, exact empty-body validation messages/order from source,
duplicate email/unknown roles, profile allowed-field selection, signed actor vs forged body,
status aliases, deduplicated roles, both reset contracts with BCrypt verification, missing/invalid
IDs and status, audit failure rollback, latest10 logs, deleted-user null relation, required phone
and invalid email domain/TLD. Tests use actual PostgreSQL17.6 Testcontainers and committed HTTP
transactions; all existing53 tests remain. The foundation closed-route check now uses orders,
since users is an authorized protected module and has its own401/403 tests.

Final counts recorded in MIGRATION_STATUS.md. Logs /tmp/aims-module4-{tests,suite,build}.log.
Frontend verification:70/70 unchanged. Source pre-existing changes preserved. No external DB,
payment/email, production admin token or default credentials used. Next MODULE5 needs authorization.
