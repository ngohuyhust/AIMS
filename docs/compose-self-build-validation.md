# Default Compose self-build validation

## Scope

This checkpoint makes `docker compose up --build` the normal local backend startup path. Compose now
includes PostgreSQL and backend by default. The backend image builds its own executable JAR with the
Maven Wrapper in a digest-pinned Maven 3.9.16/Temurin 21 stage, then copies only that JAR into the
existing digest-pinned JRE21 runtime stage. No host JDK, host Maven or prebuilt `target` is required.

Local secrets remain intentionally separate: a fresh clone runs `python3 tools/init-local-env.py`
once to create ignored mode-0600 `.env`; subsequent starts need only `docker compose up --build`.
Committed fallback credentials were not added.

## Verification

| Check | Result |
| --- | --- |
| Tests-first `python3 tools/verify-compose.py` | Failed before implementation because default services contained only PostgreSQL; passes afterward with `postgres`, `backend`, multi-stage build and Maven-version alignment |
| `docker compose up -d --build --wait` | Success; Docker built Spring Boot with Java21/Maven Wrapper and both services became healthy |
| Runtime HTTP | `/actuator/health` returned `{status:UP}`; `/api/products` returned `[]` on the fresh local database |
| Runtime hardening | Backend runs as `10001:10001`, read-only root filesystem, dropped capabilities and no-new-privileges |
| Cached rebuild | Success; builder, Maven package and runtime-copy layers were cached |
| Full backend `./mvnw verify` | 663/663 pass, zero failures/errors/skips; Java21 Enforcer and PostgreSQL17.6 Testcontainers pass; executable JAR built |
| Frontend | `python3 tools/verify-frontend.py` passes source70/70 and all approved target hashes; no Angular file changed |
| Read-only source | Still `c7c022e33f100937cd0f072c3666fd0e26754d8e`; pre-existing `.DS_Store` changes and activity-diagram directory preserved |

The first cold image build downloads its pinned builder and Maven dependencies. BuildKit reuses
them afterward. Image packaging skips tests because the separately mandatory `verify` run executes
the complete suite against PostgreSQL Testcontainers. No real payment, email or external database
service was contacted.
