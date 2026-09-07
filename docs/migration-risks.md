# Migration risks and decisions

## MODULE 3 update

- R02 resolved: mandatory environment JWT_SECRET, minimum32 UTF-8 bytes, HS256 only, no fallback.
  Local initializer adds a random key once without changing existing settings. Tests generate keys.
- Login/change-password and global CORS implemented; R15 remains only for unimplemented routes.
- Original stateless token lifetime is retained: password/status/role changes do not revoke tokens.
  Role enforcement uses exact token authorities, any requested role suffices; no ADMIN inheritance.
- BCrypt retains cost10 and the legacy 72-byte UTF-8 truncation, including long Unicode inputs.
  Future changes to password policy/token revocation require explicit compatibility decisions.
- Invalid nonstring/missing credential inputs now have controlled401/400; malformed claim sets,
  missing expiry and non-HS256 tokens are rejected instead of reproducing permissive legacy behavior.
- R10 and admin reset-password remain for MODULE4. No privileged account is bootstrapped.
- Transactional password/audit behavior is tested with actual rollback on the source varchar(50)
  audit-attribution limit; passwords remain unchanged when the audit insert fails.

## MODULE 2 update

- R01: all four user-domain tables now have source-metadata DDL and PostgreSQL schema parity tests,
  including the asymmetric junction FKs (user CASCADE/CASCADE, role NO ACTION/NO ACTION).
  Production schema drift remains unverified.
- R03 resolved for this module: V3 inserts only the three roles. There is no startup account seed,
  credential reset, reactivation or forced role reassignment. Upgrading V2→V3 preserves catalog data;
  rerunning migration preserves a synthetic deactivated account and its role membership.
- R10 remains pending for MODULE4: no admin API has been implemented. Internal entity serialization
  excludes passwordHash. JPA reads the credential field internally (unlike TypeORM select:false);
  future API responses must use reviewed DTOs and must not expose this field or log entity content.
- Shared roles use no JPA persistence/removal cascade; domain constructors receive existing roles.
  This intentionally avoids treating role creation as part of account persistence. Future admin
  services must resolve role names against the role table, matching source resolveRoles behavior.
- User status remains varchar(20) with ACTIVE default, without an invented DB enum/check constraint.
  Email uniqueness remains case-sensitive, and audit performed_by retains its source length50 even
  though emails allow100. Validation of admin inputs belongs to MODULE4.
- Since the user's history-consolidation request, all work continues on `main`; the deleted migration
  branch is not recreated. All238 original ISD commits remain ancestors of main.

## MODULE 1 update

- R01 is narrowed: seven catalog tables now have locally verified TypeORM metadata, including
  exact PK/FK/UQ/check names and indexes. A PostgreSQL test compares Flyway V2 against the original
  DDL. The deployed database may still have drift; it was never contacted.
- Numeric/date JSON and flattened subtype aliases (R12) are now checked against actual original
  TypeORM responses on an isolated fixture database. Other domains remain source-derived only.
- Catalog-only CORS/no-store is implemented for Angular connectivity. This supersedes the Phase 0
  health-only limitation (R15) for public products; auth/admin/other domains remain unimplemented.
- Explicit `status=DELETED` searches, inactive detail visibility, wildcard ILIKE, nullable CD-track
  FK and unspecified CD-track ordering are retained rather than silently tightened.
- V2 creates only absent catalog tables, with no IF NOT EXISTS or baseline adoption. It deliberately
  fails on unmanaged existing tables; importing an existing external database needs its own review.
- Test data is synthetic, isolated and rolled back; no product seeds are installed in local runtime
  or committed as production migrations. Oracle code cannot connect anywhere except localhost:55433
  database aims_oracle and does not start source application bootstrap.

Evidence: read-only NestJS/Angular source commit `c7c022e33f100937cd0f072c3666fd0e26754d8e`.
These are static code findings, not claims that a live deployment was tested or exploited.
No production database, payment gateway or email service was contacted.

| ID | Finding / source evidence | Checkpoint / disposition |
| --- | --- | --- |
| R01 | `app.module.ts` uses TypeORM `synchronize: true`; no versioned SQL/schema dump exists in source. Actual deployed schema, generated FK/check/index names and PostgreSQL version are unverified. | Before each schema module, reconstruct from entity metadata and source queries; document uncertainties. External schema-only export requires separate authorization. Phase 0 touches only a fresh local DB. |
| R02 | `auth.service.ts` and `jwt-auth.guard.ts` share a hardcoded fallback JWT secret. | Module 3 decision: require an environment secret; never copy the fallback into Java or docs. Preserve claims/expiry. |
| R03 | `user.module.ts` startup overwrites seeded users' password, ACTIVE status and roles. | User explicitly requires safe role seeding without resetting passwords. Implement in module 2, no user seeding in Phase 0. |
| R04 | Public `GET /api/orders/:orderId` returns delivery PII and `customerAccessToken`; public delivery PATCH has no ownership check. Token-protected customer endpoints coexist with these. | Direct access-control risk. User decision before module 7; Phase 0 exposes no order routes. Adding ownership checks would change the preserved frontend behavior. |
| R05 | PayPal create/capture/refund routes have no guard. Capture associates the supplied system orderId with gateway capture without checking that the stored transaction belongs to it. | Direct payment risk, decision before modules 8/9. Do not deploy those behaviors silently. |
| R06 | VietQR accepts a caller-supplied amount without comparing with order total. Merchant callbacks have no guard/signature verification; `sign` is accepted but unused. Validation compares amount/content/order but does not verify bank account, authenticity or expiry at callback time. | Direct payment risk, decision before module 10. Sandbox token is a random UUID never checked by callbacks. Keep routes unimplemented until authorized. |
| R07 | Payment success updates transaction conditionally but order status/event unconditionally; order approval/cancel/reject lack an order lock. Refund may occur before state checks, expiry updates may race callbacks. | Modules 7/8/10/11 need concurrency/idempotency/state-transition tests and an explicit compatibility decision for changed outcomes. |
| R08 | Product manager identity/quota derives from client-controlled `x-manager-id`, not verified JWT email. Quota is read outside the transaction using server-local day bounds. | Module 5 decision; preserve header presence/trim/error contract, review attribution and concurrent quota enforcement. |
| R09 | `API_BASE_URL` is fixed to localhost:3000 only for localhost/127.0.0.1; every other hostname (including 0.0.0.0) calls the legacy Render host. CORS permits every Vercel subdomain. | Frontend remains unchanged. Module 13 needs backend routing/deployment decision; module 3 must reproduce documented CORS unless user changes scope. |
| R10 | User creation returns a saved entity carrying `passwordHash` despite select:false (which only affects SELECT). Two password-reset routes expose different fields and password lengths. | Module 4: credential-hash disclosure is dangerous; user decision before returning it. Angular uses `/api/auth/reset-password/:userId`, expects `newPassword`. |
| R11 | Angular tokenless order cancel calls a PRODUCT_MANAGER-protected route; its PayPal refund path displays cancellation success while backend refund does not change order status. | Existing mismatch, record and ask at module 11; no frontend edits. |
| R12 | TypeORM PostgreSQL numeric columns are returned as strings on reads, but computed shipping/QR values are numbers. Subtype detail emits extra media aliases. Invalid inputs sometimes bypass DTO validation (`any` / interfaces); database failures can become generic 500. | Per-module response fixtures must preserve exact field names/nulls/types/status/error shapes. Do not globally serialize every BigDecimal to the same JSON type. |
| R13 | Source VietQR client logs raw QR responses; gateway errors can embed upstream response bodies. Browser sends raw EMV QR content to a third-party image service. | Modules 9/10: redact secrets/signatures, mock external calls. Frontend unchanged. |
| R14 | Source had pre-existing `.DS_Store`, `src/.DS_Store` edits and untracked ArchitecturalDesign/ActivityDiagram files. | Preserve unchanged; do not clean, stage or commit in source. |
| R15 | Phase 0 Spring Security exposes only health and denies everything else with 403. Domain routes, root greeting, JWT, Nest error envelope, no-store middleware and CORS are not yet migrated. | Deliberate scaffold scope, not an API parity claim. Public routes open in their own checkpoints; CORS/auth in module 3. Do not point users at this scaffold as a completed backend. |

## Schema boundaries

- Module 1: `products`, `media`, `books`, `cds`, `cd_tracks`, `dvds`, `newspapers`.
  Preserve decimal(12,2) prices; barcode unique; quantity >= 0; price between 0.3 and 1.5 times
  `original_value`; product→media→subtype cascading keys and nullable date/language/genre.
- Module 2: `users`, `roles`, `users_roles`, `user_audit_logs`; unique email and role name;
  audit user FK uses SET NULL; preserve join-column names and composite key/index metadata.
- Module 5: `product_logs`, JSONB changed_fields, timestamptz created_at, nullable product FK SET NULL.
- Module 7: `orders`, `order_items`, `delivery_info`, `invoices`; decimal(12,2), nonnegative totals,
  quantity > 0, unit_price >= 0, unique nullable customer_access_token varchar(64), one-to-one
  delivery/invoice keys, order cascading children, retained product references.
- Modules 8/9/10: `payment_transactions`, `paypal_transactions`, `vietqr_transactions`;
  positive payment amount, decimal(12,2), one-to-one transaction links, JSONB raw_callback,
  timestamptz expiry/payment dates. VietQR order_id is an integer, not a declared entity FK;
  transaction references are not declared unique. Do not invent constraints without review.
- Existing DB compatibility cannot be certified from annotations alone. Future native queries
  use ILIKE, RANDOM(), DISTINCT ON, ANY and row locking; PostgreSQL tests must exercise them.

## Phase 0 decisions already authorized

- Preserve existing `main` history; work on `migration/spring-boot`.
- Copy tracked frontend source/config/assets and lockfile byte-for-byte (70 files); omit generated
  artifacts, untracked caches and `.env`. The checked-in `.vscode` files are existing configuration,
  not IDE caches, and are preserved unchanged.
- Use a random ignored local database password; never reuse legacy credentials.
- V1 executes `SELECT 1` to validate Flyway on a fresh database. It is not a baseline of legacy
  business schema and creates no business tables. No destructive migration or auto-baseline.
- JWT implementation, RestClient gateway adapters and SendGrid SDK are added only in their
  respective authorized modules; Phase 0 has no inert business stubs or real provider calls.
