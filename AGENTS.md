# Controlled AIMS migration

## Authorization and checkpoint boundary

- Work on exactly ONE authorized checkpoint per turn. PHASE 0 is the initial authorization.
- After implementing, testing, reviewing, committing, pushing and reporting, STOP completely.
- Continue only after an explicit user message such as `TIẾP TỤC MODULE 1`.
- Do not implement code, entities, migrations or stubs for the next business module early.
- Do not have multiple agents edit source. Do not mass-convert NestJS with scripts.

## Source and destination

- Source `/Users/abc/Documents/Study/ITSS/ISD.20252-25` is READ ONLY, including its Git repository.
- Never modify, format, delete or commit source files. Preserve pre-existing source changes.
- Destination is this AIMS repository; origin must be `https://github.com/ngohuyhust/AIMS.git`.
- Preserve Angular byte-for-byte, including source, UI, routing, headers, authentication, URLs,
  methods, query parameters, request payloads and expected responses. No frontend fixes.
  MODULE7 explicit user exception: protect order ownership and minimally adapt token storage/headers.
  Exact approved file hashes are in docs/frontend-approved-changes.json (including related tests);
  keep docs/frontend-manifest.json and the ISD source unchanged. Do not expand that exception silently.
- Run `python3 tools/verify-frontend.py` at every checkpoint. The manifest records the source commit.

## Implementation

- Java 21, Maven Wrapper, stable Spring Boot 3.5.x; modular monolith, package by feature under `vn.aims`.
- Spring MVC, Validation, Data JPA, Security, PostgreSQL, Flyway, JWT, Actuator,
  JUnit/Mockito/Testcontainers; introduce RestClient and SendGrid SDK in their own modules.
- Constructor injection; records for immutable DTOs where suitable. Avoid unnecessary Lombok,
  Kafka, Kubernetes, microservices and dependencies.
- Preserve JSON spelling (`productID`, `orderID`, `transactionID`, `quantityInStock`,
  `totalPayment`, `paypalOrderID`, `customerAccessToken`) using Jackson annotations when needed.
- Money is BigDecimal; timestamptz uses Instant/OffsetDateTime. Preserve decimal/date JSON behavior.
- Retain source table/column names, constraints, keys, precision, JSONB, indexes and relations.
- `spring.jpa.hibernate.ddl-auto=validate` only. Use versioned Flyway migrations; never rewrite an
  applied migration, run destructive migrations, enable clean, or auto-baseline existing databases.
- Default to the isolated Docker Compose local database. Do not load legacy `.env` or connect to
  production/Supabase. Schema introspection requiring external access needs explicit authorization.
- Native queries/JdbcTemplate are allowed for PostgreSQL-specific behavior and locking.
- Record security/design problems in `docs/migration-risks.md`; do not silently change contracts.
  Bring dangerous legacy behaviors to the user's checkpoint before implementing them.
- Tests must not call real PayPal/VietQR or send real email. Never log secrets or callback signatures.

## Workflow and definition of done

1. Read relevant NestJS controllers/services/entities and Angular services/components.
2. Record method/path/auth/role/headers/query/body/response/status/errors in `docs/api-contract.md`.
3. Identify tables and business rules; state the small checkpoint scope.
4. Write/update meaningful tests before implementation, then implement only the authorized scope.
5. Run module tests, the full Java suite and Maven build. Context must load and migrations must
   run on PostgreSQL Testcontainers. No skipping tests, Docker-unavailable skips or H2 substitution.
6. Review diff for correctness and secrets; verify frontend and read-only source are unchanged.
7. Update `MIGRATION_STATUS.md`, including source hash, scope, checks, risks and pending module.
8. Commit small explainable changes; push the current branch to origin; verify exact remote hash
   and a clean working tree. A failed push means the checkpoint is NOT complete.
9. Report with exact commit hashes and remote branch, then STOP awaiting user confirmation.

- Never force push, reset --hard, rewrite pushed history or overwrite pre-existing remote content.
- Never commit `.env`, credentials/passwords/API keys, node_modules, dist, target or caches.
- Use existing Git credentials and normal Codex approvals; never request tokens in chat.
- If tests/build fail, fix within the current checkpoint or report a blocker; do not weaken tests.
- A commit cannot contain its own hash. Record implementation hashes in a later status commit;
  report the final status-commit hash and verified push in the user checkpoint.

## Checkpoint order

0. Repository/foundation only; 1. Public Product Catalog; 2. User Domain and Roles;
3. Authentication and Security; 4. User Administration; 5. Product Administration and Audit;
6. Cart and Shipping; 7. Order Placement; 8. Payment Core; 9. PayPal; 10. VietQR;
11. Order Management and Refunds; 12. Notifications; 13. Full Integration and Deployment.

## Required user report

Use the fields: `CHECKPOINT`, `Đã đọc`, `Đã triển khai`, `API đã giữ tương thích`,
`Database/Flyway`, `Kiểm thử` (command/result), `Git` (branch/commits/push/working tree),
`Frontend`, `Rủi ro hoặc TODO`, `Module tiếp theo dự kiến`.
End with `TRẠNG THÁI: ĐANG CHỜ XÁC NHẬN CỦA NGƯỜI DÙNG` and end the turn.
