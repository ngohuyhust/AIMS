# Phase 0 validation evidence

Executed 2026-09-06 on macOS arm64. Scope is foundation only; no business route parity is claimed.
The source application and production/Supabase database were not started or contacted.

## Toolchain

- Eclipse Temurin JDK 21.0.12.1+1, temporary installation:
  `/tmp/aims-java21/jdk-21.0.12.1+1/Contents/Home`.
- Official Adoptium macOS aarch64 archive SHA-256 verified before extraction:
  `3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998`.
- Spring Boot 3.5.16, Java release21 and Enforcer range `[21,22)`.
- Maven Wrapper 3.3.4 only-script distribution from Maven Central; Maven 3.9.16.
- Maven archive verified against official SHA-512, then SHA-256 pinned in Wrapper properties:
  `5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`.
- Docker Desktop engine29.5.2; PostgreSQL `postgres:17.6-alpine` in both local Compose and tests.
- Testcontainers1.21.4, dependency version managed by Spring Boot; no version override needed.

Official references: [Spring requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html),
[Maven Wrapper](https://maven.apache.org/tools/wrapper/index.html),
[Temurin archive release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1).

The first JDK transfer timed out; its incomplete checksum did not match, so it was never extracted
or used. The retry passed checksum verification. Dependencies and JDK are outside the repository;
no global Java installation was changed. Temporary tools/caches may be removed by the OS; use any
installed JDK21 and ordinary Wrapper defaults for future runs.

## Commands and results

Maven commands ran in `src/backend` with:

```sh
export JAVA_HOME=/tmp/aims-java21/jdk-21.0.12.1+1/Contents/Home
export MAVEN_USER_HOME=/tmp/aims-maven-home
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=FoundationTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

| Check | Result |
| --- | --- |
| Module test | BUILD SUCCESS, 5 run / 0 failures / 0 errors / 0 skipped |
| Full backend suite | BUILD SUCCESS, 5 run / 0 failures / 0 errors / 0 skipped |
| Maven verify/build | BUILD SUCCESS, 5 run / 0 failures / 0 errors / 0 skipped; executable JAR packaged |
| Context and MockMvc | Context loaded against PostgreSQL Testcontainer; public health UP, no components/details, other endpoints forbidden |
| Actual embedded HTTP | GET health200 and exact status UP body |
| Flyway | V1 applied; validation passed; second migrate executed0 migrations; only public table is flyway_schema_history |
| JPA/Flyway safety | ddl-auto=validate, clean disabled, auto-baseline false |
| Compose | `docker compose up -d --wait postgres` succeeded with healthy isolated DB |
| Packaged JAR | Started with Java21 against Compose at port3000; GET http://localhost:3000/actuator/health →200 `{"status":"UP"}` |
| Runtime cleanup | JAR gracefully stopped after smoke test; AIMS Compose PostgreSQL stopped with volume preserved |
| Frontend | `python3 tools/verify-frontend.py` PASS, 70 files equal in source/target/baseline SHA-256 and file sets |
| API inventory | All44 Nest controller method/path declarations found in api-contract.md; no Java business API implemented |
| Source preservation | SHA-256 of tracked source files unchanged since initial snapshot; source commit/status unchanged |
| Secret review | Reviewed added/config files and scanned private-key/GitHub/SendGrid/AWS/credential-URL patterns; none found; .env ignored |
| Diff review | Reviewed added Java/config/tools/docs; whitespace check excludes the byte-preserved legacy frontend. Official Windows launcher retains CRLF via Git attributes. |

Raw execution logs are local, not committed (to avoid publishing environment-specific details):
`/tmp/aims-phase0-module-test.log`, `/tmp/aims-phase0-suite.log`, `/tmp/aims-phase0-build.log`,
`/tmp/aims-phase0-runtime.log`, `/tmp/aims-phase0-smoke-result.txt`.
Surefire XML/text reports are in ignored `src/backend/target/surefire-reports`.
JUnit tests were never disabled, weakened, skipped or replaced by H2. Java21 emitted the standard
Mockito/Byte Buddy dynamic-agent notice; all tests completed successfully.

## Limits of this checkpoint

- No business tables/entities, JWT, gateway adapters, email provider, CORS port or Nest error
  mapper is implemented. Tests prove only the new foundation contracts.
- Frontend build is explicitly deferred to MODULE13; frontend content preservation was verified.
- Legacy serialization is documented from source, not asserted against a live legacy API yet.
- Legacy schema constraint/index names and deployed PostgreSQL version remain unverified;
  V1 does not claim to baseline or validate the old schema.
- Exact commits and push state are recorded in MIGRATION_STATUS.md and the final checkpoint report.
