# MODULE 3 validation — 2026-09-07

Source read-only commit: c7c022e33f100937cd0f072c3666fd0e26754d8e.
Read auth controller/service/JWT and roles guards, bootstrap CORS, Angular AuthService and existing
contract. Scope: login, JWT claims/verification, BCrypt, change-password with audit transaction,
exact-role method authorization, global CORS. Admin reset-password remains MODULE4.

Dependency: Spring-managed spring-security-oauth2-jose6.5.11/Nimbus, following
[Spring JWT documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
Production requires JWT_SECRET; no fixed fallback. Test-only configuration generates a secret.

Commands in src/backend with JAVA_HOME=/tmp/aims-java21/jdk-21.0.12.1+1/Contents/Home
and MAVEN_USER_HOME=/tmp/aims-maven-home:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=AuthIntegrationTest,JwtTokensTest clean test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

Results: module13/13, full53/53, verify53/53, zero failures/errors/skips. Executable JAR built.
PostgreSQL17.6 Testcontainers, Hibernate validate and existing V1–V3 migrations passed. No new DDL.
All40 previous tests retained. Frontend verifier70/70 and git diff --check passed.

Coverage: login201/claims24h/Unicode user/credential exclusion, wrong password and deactivation401,
change-password201/hash/audit, short-new/wrong-old400, missing/wrong-case/malformed Bearer401,
expired/future-nbf/wrong-key/HS512/unsigned token rejection, mandatory secret, original bcryptjs
synthetic hash and long Unicode72-byte limit, CORS preflight/disallowed-origin/stale public token,
role case sensitivity/any-role/ADMIN-not-manager, real password rollback on failed audit insert.
Synthetic bcrypt fixture was generated with the source installed bcryptjs without starting NestJS.
Tests use an isolated audit failure transaction, not a mocked rollback claim.

Initial compile/test iterations exposed a cross-package helper access and transaction-fixture
flush/cache issues; these were corrected before the final clean module run. No tests were skipped.
Logs: /tmp/aims-module3-{tests,suite,build}.log. Source pre-existing changes are preserved.

Local init appends only a missing random JWT key; runtime .env remains ignored. Normal app startup
must export it. No default privileged account, deployed DB, real payment/email or frontend change.
Remaining limitations and deliberate malformed-input handling are in docs/api-contract.md and
docs/migration-risks.md. Production drift and full end-to-end deployment remain out of scope.
