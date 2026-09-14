# Spring Security request-boundary refactor validation

## Scope and outcome

This checkpoint completes the prior login refactor at the HTTP authentication boundary. The custom
`OncePerRequestFilter` no longer parses JWTs or writes `SecurityContextHolder`. The backend uses the
Spring Boot OAuth2 Resource Server starter, `BearerTokenAuthenticationFilter`, `JwtDecoder`,
`JwtAuthenticationConverter`, Spring `Jwt` principals and Spring request/method authorization.
Token issuance uses `JwtEncoder`, including the independently keyed VietQR merchant token.

The intentionally scoped `BearerTokenResolver` preserves the existing contract: user/manager routes
authenticate Bearer tokens; public catalog/login and customer order-capability routes ignore an
unrelated stale token. Customer capabilities and merchant Basic credentials are distinct API
schemes, not duplicate implementations of user JWT authentication.

No URL, method, header, request/response JSON, JWT claim/lifetime, error envelope or database schema
changed. Angular therefore required no edit.

## Verification

Commands run through the Maven Wrapper in the existing `eclipse-temurin:21-jdk` image, with
PostgreSQL 17.6 supplied exclusively by Testcontainers.

| Check | Result |
| --- | --- |
| Tests-first resource-server assertion | Confirms Spring `BearerTokenAuthenticationFilter` is installed and the deleted custom filter is absent |
| Authentication/JWT tests | 15/15 pass |
| Affected integration/unit tests | 80/80 pass across product, user, order, PayPal and VietQR |
| Full backend suite (`./mvnw test`) | 663/663 pass; zero failures, errors or skips |
| Maven build (`./mvnw verify`) | 663/663 pass; Java 21 Enforcer passes and executable JAR builds |
| Flyway/JPA | V1-V10 validated/applied on PostgreSQL Testcontainers; `ddl-auto=validate`; no migration changed |
| Frontend integrity | `python3 tools/verify-frontend.py` passes: source 70/70; target 67 unchanged, 3 approved edits and 2 approved additions |
| Source integrity | Read-only source remains at `c7c022e33f100937cd0f072c3666fd0e26754d8e`; pre-existing `.DS_Store` changes and untracked activity diagram are preserved |

No test contacts PayPal, VietQR, SendGrid, Supabase or another production service. No JDK was
downloaded; the repository remains pinned to Java 21 as required by its Maven Enforcer rule.
