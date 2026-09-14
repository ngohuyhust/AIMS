# Spring Security authentication refactor validation

## Scope

This checkpoint replaces manual username/password lookup and comparison in `AuthService` with
Spring Security's `AuthenticationManager`, `DaoAuthenticationProvider` and `UserDetailsService`.
The existing bcryptjs-compatible encoder, JWT implementation, stateless authorization filter,
HTTP/JSON contract, schema and frontend are unchanged.

## Verification

Commands ran through the Maven Wrapper in the existing `eclipse-temurin:21-jdk` image. The Docker
socket and `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` let the unchanged Testcontainers
tests use PostgreSQL 17.6 from inside that Java 21 environment.

| Check | Result |
| --- | --- |
| Authentication integration test | 11/11 pass; manager success, authority mapping, credential erasure, bad credentials and disabled accounts covered |
| Full backend suite (`./mvnw test`) | 662/662 pass; zero failures, errors or skips |
| Maven build (`./mvnw verify`) | 662/662 pass; Java 21 Enforcer passed and executable JAR built |
| Flyway/JPA | All V1-V10 migrations validated/applied on PostgreSQL Testcontainers; `ddl-auto=validate`; no migration changed |
| Frontend integrity | `python3 tools/verify-frontend.py` passes all 70 source files and the approved overlay |
| Source integrity | Read-only ISD source remains at `c7c022e33f100937cd0f072c3666fd0e26754d8e`; its pre-existing status is preserved |

No test contacted PayPal, VietQR, SendGrid, Supabase or another production service. No credential,
schema, frontend or deployment setting changed.
