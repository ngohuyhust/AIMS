# Code and repository cleanup validation

## Scope

This checkpoint implements the concrete cleanup candidates reported by the preceding read-only
audit. It removes two redundant dependency declarations, one conflicting legacy logging transitive,
four unreferenced offline capture scripts, an empty Angular stylesheet, unused root state and
frontend debug logging. The user explicitly
authorized the frontend cleanup after being shown the affected files and token-console risk.

The checkpoint does not refactor the large product-management component, rewrite Spring Security
matchers, alter the API, touch Flyway migrations or modify the read-only NestJS source.

## Verification

- Maven dependency tree retains `flyway-core` transitively through
  `flyway-database-postgresql`; the OAuth2 resource-server starter retains the required Spring
  Security modules without the generic starter declaration. SendGrid retains its HTTP client while
  the redundant `commons-logging` artifact is absent and Spring's `spring-jcl` bridge remains.
- Java 21/Maven 3.9.16 Docker `mvn -B verify`: 663 tests, zero failures, errors or skips;
  PostgreSQL 17.6 Testcontainers, Flyway V1–V10 and executable JAR pass.
- `npm test -- --watch=false`: two files and 7/7 tests pass.
- `NG_BUILD_MAX_WORKERS=2 npm run build`: production bundle passes on Node 24.16.0. The sandboxed
  invocation aborted before compilation with exit 134; the approved non-sandboxed build succeeded.
- `python3 tools/verify-frontend.py --source-ref`: source 70/70; target 62 unchanged, seven approved
  edits, three approved additions and one approved deletion; all hashes pass.
- `python3 tools/verify-compose.py` and `git diff --check`: pass.
- `docker compose up -d --build --wait`: rebuilt both services; backend and frontend are healthy,
  actuator reports `UP`, the frontend returns HTTP 200 and the public catalog returns 182 products.
- No production database or provider was contacted. The sibling NestJS repository remains at
  `c7c022e33f100937cd0f072c3666fd0e26754d8e` with its pre-existing unrelated worktree state.

The first Java 21 container attempt used `./mvnw` with a fresh named Maven cache and stopped before
compilation because the downloaded Maven distribution failed the wrapper checksum. The same exact
Maven 3.9.16/Temurin 21 image then ran its bundled `mvn`; the complete required suite passed without
skips or weakened checks.
