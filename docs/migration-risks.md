# Migration risks and decisions

## MODULE 11 update

- User chose paid-order cancellation only by PM. Customer token grants unpaid cancellation only;
  paid cancellation returns403. No frontend change or indirect customer PayPal refund bypass.
- Source refunded before checking transition and restored stock without durable action identity.
  V9 reserves the action under the order lock before remote refund; approval/payment begin/delivery
  cannot interleave with cancellation. PayPal direct refunds cannot start on APPROVED orders.
- Remote refund and database finalization cannot be one atomic transaction. Existing PayPal journal
  plus lifecycle reservation permit retry after a local failure without another refund/stock restore.
  Pending/ambiguous payment outcomes require reconciliation; do not reset the action or force stock.
- PM may still approve unpaid PENDING orders as source; payment core then forbids creating payment
  for APPROVED orders. This source business rule is retained and requires operational care.
- VietQR confirmation records the manager's manual refund; no bank transfer is performed. Same-order
  locks and conditional payment transition prevent repeat stock restore/refund confirmation.
- New table is auxiliary only; original schema preserved. Sorted product locks, after-commit events,
  manager token redaction and parameterized list filters retain existing module safeguards.
- MODULE12 must address durable notification delivery; no email sent by MODULE11.


## MODULE 10 completed implementation

- User approved order-capability QR create/status, merchant bearer callbacks and PM-only sandbox
  test trigger disabled by default; minimal frontend service headers/mapping/test are authorized.
  Public callbacks and source untracked UUID token are replaced with authenticated300s merchant
  tokens isolated from user JWTs. The optional source sign field is not a verified HMAC; host-to-host
  bearer authentication is the implemented protocol. No production deployment is claimed.
- Callback validates bank account, exact amount/content, canonical order ID, credit type, timestamp
  and unambiguous payment match. Receipt uniqueness prevents a bank transfer settling two payments.
  Ambiguous historical references, expired/late transfers and malformed proof require reconciliation;
  no cross-order fallback or automatic QR refund/stock release. Bank/sign omitted from raw_callback.
- QR PAID, shared SUCCESS, order state and receipt commit together; expiry to EXPIRED/FAILED is also
  atomic and shares order locking. Source races/unconditional expiry writes are not preserved.
  Durable receipt table is a deliberate addition; original VietQR table metadata remains exact.
- Intent persists before remote generation. Retrying generation may create another remote QR for
  the same local payment after a lost response; it never charges funds. Old pending rows expire on
  status/create, not a background job. Delivery remains frozen until payment fails or is reconciled.
- Test trigger requires explicit enablement, PM JWT and exact sandbox host; it does not hold an order
  lock while an external service may callback synchronously. Default customer test button remains
  visible to preserve UI but receives403. Production deployment must keep the test flag false.
- Only local mock gateway/Testcontainers used; no real credentials, callbacks or bank transfers.
  Notifications remain in-process/non-durable; MODULE11/12/13 own later lifecycle/delivery/deployment.

## MODULE 9 update

- User approved token-owned PayPal create/capture and PRODUCT_MANAGER-only refund; source public
  money endpoints are deliberately closed. Angular changes are limited to approved service headers
  and their test. Original ISD frontend and original manifest remain unchanged.
- Source ignored capture orderID and accepted top-level COMPLETED without validating capture amount.
  The adapter now binds order/transaction/gateway ID and verifies reference/currency/amount and actual
  capture completion before confirming. Pending capture/refund is409, avoiding false UI success.
- V7 adds a deliberate auxiliary paypal_operations journal while retaining original PayPal table
  names, columns, nullable relation, unique key and cascade exactly. Journal responses can contain
  provider PII needed for recovery/raw response compatibility: do not log them or expose database
  access. Retention/reconciliation procedures are still an operational follow-up, not an admin API.
- Stable request UUID and per-operation DB lock prevent duplicate local POSTs after journal commit.
  Crash before journal commit retries the same UUID within5h; beyond that, block for reconciliation.
  A lost result is never converted blindly into FAILED/new payment. Pending results refresh via GET.
  This is bounded provider idempotency, not an unbounded exactly-once financial guarantee.
- Local apply failures preserve the journal; PayPal/shared/order updates roll back together. Refund
  is terminal and late capture/create application cannot downgrade it. Core after-commit events are
  still non-durable; notification delivery remains MODULE12. Cancellation/stock restore is MODULE11.
- Upstream errors are sanitized502/503 rather than source400 with provider details. OAuth is cached,
  HTTPS required outside loopback, redirects disabled; no live credentials or gateway calls in tests.
  Fixed source conversion25,000 VND/USD uses approved BigDecimal/HALF_UP; not a market rate.
- Unknown/PENDING outcomes can hold delivery/payment switching until reconciled. No expiry job or
  manual override endpoint is introduced. Live sandbox acceptance/deployment remains MODULE13.


## MODULE 8 update

- User chose to freeze delivery while pending/paid and validate whole-VND amount against order.
  A shared order-row lock serializes delivery edits, begin, fail and confirm. Tests prove that an
  edit and creation using the old amount cannot both succeed; failed attempts allow repricing.
- Shared transaction and order confirmation are now one transaction; association/method/amount
  checks precede mutation. SUCCESS/REFUNDED repeats do not touch orders or emit another event.
  FAILED remains terminal; late external charges need provider reconciliation, not silent revival.
- Coalesce matching pending attempts under the order lock and reject conflicting active attempts.
  This is local idempotency, not external exactly-once charging. MODULE9/10 must apply the stable
  shared transaction ID to provider idempotency and define safe method switching/expiry behavior.
- ORDER_PAYMENT_SUCCEEDED is published after commit only. In-process event delivery is not durable;
  process crashes/consumer failures can lose notification delivery. MODULE12 must resolve reliable
  notification/reconciliation needs. Do not claim exactly-once email from this core implementation.
- Core markRefunded only records a verified full-refund result, conditioned on SUCCESS. It neither
  contacts a provider nor restores stock/changes order status; eligibility and orchestration remain
  MODULE9/MODULE11. No unsafe latest-transaction unconditional refund implementation was copied.
- Real payment_transactions now backs protected order-detail paymentMethod. V6 preserves exact
  metadata/defaults/nullability/decimal/check/FK; no PayPal/VietQR tables or inverse entity stubs.
- R05/R06 remain open for gateway modules: callbacks must authenticate provider proof and verify
  provider/order/currency/amount linkage. No payment HTTP endpoint is exposed in MODULE8.

## MODULE 7 update

- R04 resolved for implemented detail/delivery routes by explicit user decision: token protection
  and minimal frontend edits allowed. Require per-order capability; PRODUCT_MANAGER JWT permits
  read only and its response omits the capability. Missing/wrong/cross-order cases tested.
- Original source70-file SHA-256 baseline remains authoritative for ISD. A separate approved overlay
  covers only the two frontend services, capability helper/test and obsolete shell-test correction.
  No UI/route/provider changes. Guest tokens stay per-tab (sessionStorage/memory), never on all HTTP
  requests; reload works, closing storage requires the original token link. XSS protection and token
  revocation/expiry are not newly implemented; source capability has no expiry.
- Framework web payload/bind/extraction and SQL exception-detail logging are disabled by default
  to avoid capability/PII disclosure. Customer-link query tokens still require care in external
  proxy/access logs; no application access logger or provider is added here.
- Sorted product locks remove source input-order deadlock risk; all ACTIVE/stock decisions occur
  under lock. Order/items/delivery/invoice and stock reserve are atomic. Delivery edits also lock
  their order, so concurrent edits cannot mix invoice and order totals. Tests cover last-unit
  contention, opposite cart orders, forced failures and monetary overflow rollback.
- V5 exactly matches original four order tables; V1–V4 remain untouched. Upgrade test preserves
  existing V4 product data. Product administration now checks real order_items constraints.
- PENDING_PROCESSING delivery edits still reprice shipping as source. Before payment transitions
  are enabled, MODULE8 must resolve whether edits after successful payment are prohibited or
  reconciled; payment locking/idempotency is not silently implemented in MODULE7.
- Detail returns paymentMethod:null while payment_transactions is absent; once it exists, query
  SUCCESS normally. No payment table/service stub. MODULE8 must retest this against its real schema.
- Placement is public, reserves stock, and has no idempotency key/abandoned-order expiry in source.
  These lifecycle limits persist until later authorized modules; stock-check is not a reservation.

## MODULE 6 update

- User approved **BigDecimal + HALF_UP** after seeing the source binary-rounding discrepancy:
  subtotal0.35 produces VAT0.03 in NestJS but0.04 in Java. Monetary values remain JSON numbers;
  exact decimal calculation and non-overflowing quantity sums are deliberate corrections.
- Shipping quotes preserve source inclusion of DELETED/inactive/out-of-stock products. A quote
  grants no purchase eligibility. Stock checking is stateless and cannot guarantee later stock;
  MODULE7 must validate ACTIVE and quantity again inside its placement transaction/locks.
- Weight-only remains active. Volumetric is an available tested alternative, not a pricing switch.
- R04 stays unresolved for MODULE7: only public stock/quote endpoints are enabled now. No customer
  PII, order details, access tokens, payment actions or delivery edits are exposed by MODULE6.
- Fresh PostgreSQL integration confirms Flyway still atV4; no order/cart tables or migrations.
- Invalid JSON uses sanitized400 instead of Express-specific parser text. Pathological nested
  DTO shapes and numbers beyond JS safe precision are not claimed byte-for-byte compatible.

## MODULE 5 update

- R08 resolved by explicit user choice: "Email JWT + khóa quota (khuyến nghị)". Still require
  x-manager-id, but audit/quota use signed email. PostgreSQL per-manager advisory transaction lock
  prevents concurrent quota bypass; product locks are taken in sorted order. Header spoofing and
  racing requests at19/20 are tested (only one request succeeds). ADMIN does not imply manager.
- Audit INSERT uses clock_timestamp after locks so a transaction waiting across midnight is not
  logged at its pre-lock start time. Daily range follows the server timezone, as the source does.
- V4 introduces only product_logs, exactly matching original TypeORM columns/PK/FK/defaults/indexes;
  JSONB and nullable SET NULL product reference verified on PostgreSQL. V1–V3 unchanged.
- The source batch-delete reads order_items, scheduled MODULE7. Before that migration, missing
  public.order_items means no stored order references; when present it is queried normally.
  SQL errors on an existing table fail/roll back rather than treating reference checks as false.
  No orders schema/stubs introduced; MODULE7 needs full-schema integration retesting.
- Subtype/audit failures roll back all product writes. Price calculations use BigDecimal; stock
  decrement concurrency proves no negative quantity or duplicate successful decrement.
- Existing stock-edit-on-DELETED/post-commit404 and repeated-deactivation quota behavior remain.
  Broad lifecycle/status changes are not silently introduced. Strict date/null-subtype and
  compound-invalid-input precedence limits are recorded in the contract.

## MODULE 4 update

- R10: admin create/list/update/log responses deliberately omit credential hashes. The existing
  User JsonIgnore boundary is preserved through explicit maps. Reset endpoints remain ADMIN-only
  and retain their distinct intentional temporary-password response contracts.
- Original invalid-role/status/not-found/duplicate-email errors, aliases, nullable fields,
  audit attribution and 10-log cap are tested. Unauthenticated users401, STAFF/PM/lowercase admin403.
- All user/role/status/reset writes are transactional with audit; a real failed audit insert leaves
  no partial user. Source actor varchar(50) limitation remains. No schema change or account seed.
- Existing tokens retain role claims until expiry, including after deactivation/role changes.
  Source allows self-demotion/deactivation and concurrent last-writer updates; no new rules invented.
- Safe deviations: no hash disclosure, int32 overflow/nonstring-role400, guaranteed12-character
  random reset string. Exotic email-validation parity is unverified; see contract limitations.

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

## MODULE 12 notification delivery

Durable V10 snapshots close the source in-process event-loss gap between database commit and enqueue.
Email provider errors are isolated from committed business state; inability to persist the outbox
rolls back the associated business transaction. Default delivery is off; enabling it drains queued
notifications, including older events. Operators must review the backlog and sender/public URL first.
At-least-once delivery can duplicate after remote acceptance/local commit failure; SENT is acceptance,
not proof of inbox delivery. Six attempts then FAILED; no bounce webhook or automatic reconciliation.
Outbox payload includes delivery PII/order token: restrict access and define retention before production.
No provider response body, recipient or token is logged. Paid emails omit the customer Cancel button
because the user chose PM-only cancellation. No frontend exception was expanded.

## MODULE 13 deployment boundary

Local production-profile image and integration tests pass; deployment artifacts/CI do not constitute
a live production cutover. Frontend still targets the legacy Render hostname outside localhost.
An authorized replacement at that hostname or a separately approved frontend URL edit is required.
Production database metadata/import/baseline, initial ADMIN provisioning, TLS and live gateways need
rehearsal/operational approval. Production profile requires explicit DB/HTTPS frontend configuration
and disallows sandbox callback trigger; it does not silently change legacy CORS or select live gateways.
See docs/deployment.md for environment, rollout and rollback; no production access was performed.

## Isolated legacy Supabase snapshot

With explicit user authorization, the legacy PostgreSQL endpoint was inspected read-only and a new
`aims_java` schema was created beside `public`. Flyway V1-V10 and 1,521 source rows were copied and
compared inside a repeatable-read transaction. NestJS `public` was not modified and traffic was not
switched. The snapshot is not change-data-capture: any later NestJS writes remain only in `public`.
There are 27 historical orders without customer access tokens and unresolved pending/refund payment
states; reconcile them before cutover. Provider credentials were not copied and notifications remain
off. Details and rollback boundary: [legacy Supabase migration](legacy-supabase-migration.md).
