# Existing AIMS API contract

Source commit: `c7c022e33f100937cd0f072c3666fd0e26754d8e` in the read-only sibling
`ISD.20252-25`. This is a static inventory of existing NestJS behavior and Angular consumers,
recorded before any Java business module. It is NOT a claim of parity with the Phase 0 scaffold.
No source application was started: its startup synchronizes schema and resets seeded users.
Runtime snapshots against an isolated legacy database must be added in the relevant checkpoints.

## MODULE 1 implementation evidence

The three public catalog GET routes are now migrated. Source TypeScript catalog entities/repository/
service ran against a disposable local PostgreSQL database, without importing AppModule/main or
loading source `.env`. Real TypeORM JSON snapshots are checked in under
`src/backend/src/test/resources/catalog`; MockMvc compares the entire response, including nulls,
money strings, millisecond UTC timestamps, date-only values, subtype aliases and CD tracks.
The captured schema is in [module-1/typeorm-schema.sql](module-1/typeorm-schema.sql); tests compare
column definitions/defaults/nullability/precision, named constraints and indexes against Flyway V2.
This proves source-derived local schema parity, not the state of an external deployed database.

Catalog HTTP includes source-compatible CORS and `Cache-Control: no-store`, no ETag, public GET/HEAD,
and 400/404/500 Nest error envelopes. CORS is scoped to `/api/products` so Angular catalog works now;
JWT and other features' security/CORS remain for MODULE 3. Admin methods/audit routes remain denied
until their own modules. The original five foundation tests were retained: their expected migration
version/table set and closed-route example were updated to reflect the newly authorized catalog.
No API contract expectation was weakened to conceal a failure.

Additional edge cases preserved: whitespace-only numeric bounds mean0, empty bounds are absent,
hex/binary/octal numeric strings are accepted as JavaScript Number does, nonfinite/malformed bounds
are400, integer IDs outside PostgreSQL's int32 range are500, unknown productType detail is400.
The source allows explicit `status=DELETED` search although detail excludes deleted products; this
is retained. CD tracks have no promised sort order in the source, so no new sort is introduced.

## MODULE 11 order management/refunds

User explicitly chose **only PRODUCT_MANAGER may cancel paid orders**. Customer cancellation keeps
POST /api/customer/orders/:orderId/cancel?token=... for owned unpaid orders, returns403 for paid
orders,400 missing token and404 wrong token/order. Frontend unchanged. Manager responses omit the
customer capability, continuing MODULE7's ownership policy.

PM JWT routes (including trailing slash): GET/HEAD /api/orders/pending and /api/orders/vietqr-refunds;
POST /api/orders/:orderId/approve, /reject, /cancel and /confirm-vietqr-refund. GET200 pagination
{items,total,page,limit,totalPages}; POST201 full order graph with persisted decimal strings, UTC
millisecond dates and original JSON field names. Missing JWT401, wrong role403, invalid numeric
path/page/limit400, missing order404; invalid transition400; payment/action conflicts409; upstream
refund502/503 sanitized; unexpected500. Empty POST body allowed, no new request fields.

Lists preserve page>=1 and limit1..30, source search receiver/email/phone/#order ID, TODAY/WEEK(last7
calendar days)/MONTH from server-local midnight, dynamic latest SUCCESS payment filter or UNPAID.
Pending includes PENDING_PROCESSING then PENDING, oldest first; refunds includes REFUND_PENDING
with source VIETQR fallback/filter behavior. A stable order-ID tie breaker and repeatable-read
snapshot avoid duplicate/drifting pages. Unknown dateRange ignored; non-VIETQR refund-list payment
filter ignored as source. Source wildcard LIKE semantics retained, SQL values parameterized.

Source allows PM approval/rejection/cancellation from PENDING or PENDING_PROCESSING, including
approval of unpaid PENDING orders; other states reject. Newly protected: unresolved PENDING payment
blocks lifecycle changes; cancellation journal blocks new payments, delivery edits and competing
approval. Direct PayPal refund cannot start after order approval. Duplicate matching cancellation
returns its completed result without stock/event duplication; different actions reject.

V9 adds order_lifecycle_operations, durable per-order CANCEL/REJECT reservation with payment binding
and PENDING/COMPLETED outcome. No original business-table or V1–V8 change. PayPal refund runs without
order/product locks using MODULE9's journal. After verified REFUNDED, atomically restore stock in
ascending product order, finalize CANCELLED/REJECTED and complete the action. Local failure after
remote refund leaves a resumable action; retry skips the refund and restores stock once. VietQR
paid cancellation restores stock once and sets REFUND_PENDING; PM confirmation sets shared REFUNDED
and order REFUNDED without another stock restore. It records a manual transfer, never sends one.

Lifecycle events ORDER_APPROVED/ORDER_REJECTED/ORDER_CANCELLED carry IDs/refund status after commit;
no event on rollback/duplicate. Delivery is not durable yet; MODULE12 follows. Ambiguous payment
history/unknown provider needs reconciliation. No forced cancellation of uncertain payments, no
stock release before confirmed PayPal refund and no cancellation of APPROVED/delivery states.

## MODULE 10 VietQR

User approved the proposed protections by asking to continue: order capability for QR create/status,
merchant bearer token for callbacks, PRODUCT_MANAGER-only sandbox trigger disabled by default.
Angular service headers/payment-to-order mapping and related test are the only frontend changes.

| Method/path (also trailing slash) | Authorization | Input | Success |
| --- | --- | --- | --- |
| POST /api/vietqr/payments | x-order-token for orderId | {orderId,amount,content} | 201 payment response |
| GET/HEAD /api/vietqr/payments/:paymentId/status | x-order-token for associated order | int32 path ID | 200 payment response |
| GET/HEAD /api/vietqr/payments/by-ref/:reference/status | x-order-token for associated order | reference path | 200 payment response |
| POST /api/vietqr/payments/:paymentId/trigger-callback | PRODUCT_MANAGER JWT; feature enabled; sandbox | path ID | 201 {status:"SUCCESS"} |
| POST /api/vietqr/payments/callback | merchant bearer token | original VietqrCallbackDto | 201 {status:"SUCCESS",message,paymentId} |
| POST /vqr/api/token_generate | Basic merchant credentials | no body | 201 {access_token,token_type:"Bearer",expires_in:300} |
| POST /vqr/bank/api/transaction-callback or transaction-sync | merchant bearer token | callback fields/aliases | 201 {status:"SUCCESS",message,paymentId} |

No query parameters. Payment response preserves paymentId, orderId, numeric amount, transactionRef,
content/paymentContent, qrCode, qrLink, expiredAt (millisecond UTC), status, bankCode, bankAccount and
bankAccountName. Original create/callback DTO behavior/error order and whitelist captured in98 cases.
Create accepts source numeric conversion; callback numeric fields remain strict. Merchant routes also
map bankAccount, transactionId, transactionTime, referenceNumber, orderid and numeric strings. Invalid
or malformed bodies400; missing/invalid credentials401; wrong role/disabled trigger403; wrong order
capability404; ambiguous/terminal/conflicting state409; sanitized provider502/503, unexpected500.
API and merchant responses use no-store. No raw upstream error, credential or callback signature logs.

Merchant JWT lasts300seconds, is signed using a domain-separated key derived from JWT_SECRET and
validates issuer/audience/subject/issued/expiry times. User JWTs cannot authenticate callbacks; merchant
JWTs cannot authenticate user routes. Basic credentials are checked in constant time; unset merchant
configuration fails closed. This follows the provider host-to-host bearer protocol, not an invented
`sign` algorithm: [VietQR integration documentation](https://doc.vietqr.vn/doc/api-vietqr-callback/api-vietqr-host2host/integrated-document-for-payment-service-vietqr).

V8 retains exact legacy vietqr_transactions schema and adds unique bank-receipt ledger. Intent is
committed before QR generation; retry reuses the same PENDING payment. Generation serializes on the
order lock and uses the stored normalized content. Source amount is rounded whole VND HALF_UP and
compared to the order. Outbound Basic/token/generation paths and response aliases remain compatible.
A lost QR-generation response can cause regeneration for the same payment; QR generation does not
debit money and no unsupported provider idempotency guarantee is claimed.

Callbacks verify bank account, canonical positive order ID, exact amount/content, credit type, valid
timestamp and one unambiguous reference or order/content/amount candidate. No fallback across orders.
A receipt cannot settle two payments. PAID/shared SUCCESS/order transition and receipt insert commit
atomically; duplicates do not re-emit the shared event. Expiry/status and callback share the order lock;
expiry marks QR EXPIRED and shared FAILED together. Late/ambiguous transfers require reconciliation,
not revival. Creation expires old pending rows before beginning a new attempt. Refund and stock/
order cancellation remain MODULE11. Shared notification event is still non-durable (MODULE12).

Configuration: VIETQR_API_BASE_URL defaults https://dev.vietqr.org; outbound VIETQR_USERNAME,
VIETQR_PASSWORD, VIETQR_BANK_CODE, VIETQR_BANK_ACCOUNT, VIETQR_BANK_ACCOUNT_NAME are required for
creation. VIETQR_MERCHANT_USERNAME/PASSWORD are independent inbound credentials. TTL defaults15min
via VIETQR_PAYMENT_TTL_MINUTES; invalid/nonpositive/over-one-year values fall back to15min.
VIETQR_ENABLE_TEST_CALLBACK defaults false. When enabled, require PM JWT and exact dev.vietqr.org
host (or loopback for local tests); a substring/fake sandbox host is rejected. Triggering holds no
order transaction while the provider may callback synchronously. It does not directly mark PAID.
Angular remembers paymentId->orderId in memory after create/reuse, then attaches only that order's
capability while polling; page reload creates/reuses QR again before polling, preserving the UI flow.

## MODULE 9 PayPal

User explicitly approved **create/capture use the order capability; refund only PRODUCT_MANAGER**,
including minimal Angular payment-service headers and related tests. No UI, URL or payload edits.

| Method/path (also trailing slash) | Authorization | Body | Success |
| --- | --- | --- | --- |
| POST /api/paypal/order/create | x-order-token belonging to orderID | {orderID:number} | 201 {paypalOrderID,status,approveUrl?} |
| POST /api/paypal/order/capture | x-order-token belonging to orderID | {paypalOrderID:string,orderID:number} | 201 raw verified completed PayPal order |
| POST /api/paypal/order/refund | Bearer JWT with PRODUCT_MANAGER role | {orderID:number} | 201 raw verified completed PayPal refund |

No query parameters. Unknown body fields ignored; numeric strings rejected. Original DTO validation
messages/order (including duplicate refund errors and `Mising orderID`) are captured in39 offline
fixtures. Positive fractional IDs pass source DTO but cannot address an int32 DB key: sanitized500.
Missing capability/JWT401, wrong ownership404, wrong role403, mismatched gateway ID400, incompatible
payment state409. Invalid JSON400; unexpected server errors500. Provider errors are sanitized502,
unknown transport outcome503. Source broadly returned400 with upstream text; leaking that text is
not preserved. Cache-Control:no-store and existing CORS apply. Other methods remain closed.

Create derives amount from the locked order via shared PaymentService, rounds VND HALF_UP, then
converts with the source fixed **25,000 VND/USD** to two decimal places HALF_UP. This is the source
application's fixed conversion, not a current exchange-rate claim. OAuth uses client credentials,
Basic/form token request and a cached expiring bearer token. Orders v2 create/capture and Payments v2
full refund use RestClient, 3s connect/10s read timeouts, no redirects and stable PayPal-Request-Id.
Create preserves intent CAPTURE, reference_id, description, branding and return/cancel query strings.
Prefer:return=representation requests full data for verification; capture/refund success preserves raw
provider JSON. Verify gateway ID, order reference, USD currency, amount and completed capture/refund.
Incomplete capture/refund returns409 so Angular cannot mistakenly treat any201 as paid/refunded.

V7 preserves original paypal_transactions metadata exactly and adds paypal_operations, a durable
operation journal (one CREATE/CAPTURE/REFUND per shared transaction, stable UUID, creation time and
JSONB response). Three transactions commit intent, serialize/journal remote outcome, then atomically
apply PayPal/shared/order changes. External calls hold only the operation lock, not the order lock.
Concurrent duplicates reuse journal results; failed local apply retries without another money POST.
Uncertain transport retries reuse the same request ID within5h; later attempts return409 requiring
reconciliation. Known pending results refresh with GET, not another capture/refund POST. Cached
completed results may be applied after5h without a new external charge. Provider idempotency is
bounded; this is not an unconditional exactly-once claim. See [PayPal idempotency](https://developer.paypal.com/reference/guidelines/idempotency/)
and [Orders request-ID retention](https://developer.paypal.com/serversdk/net-standard-library/api-endpoints/orders/create-order/).

Refund records shared/PayPal REFUNDED only after verified COMPLETED. It does not cancel the order,
restore stock or send email (MODULE11/12). Pending or ambiguous payments remain PENDING and keep
delivery frozen. No arbitrary retry with a new payment identity or automatic failure/expiry.
Configure PAYPAL_CLIENT_ID, PAYPAL_CLIENT_SECRET, optional PAYPAL_API_BASE_URL (sandbox default),
and APP_PUBLIC_URL (http://localhost:4200 default) in the backend process environment. No credentials
are committed or read from the source; tests use only a loopback mock, never live PayPal.

## MODULE 8 payment core

No payment/controller endpoint is added. PayPal/VietQR/provider callbacks remain denied403 until
MODULE9/MODULE10. PaymentTransaction and V6 add only shared payment_transactions; no provider rows,
credentials, SDKs, adapters, email listeners or external calls. CreditCardGateway and QrCodeGateway
retain separate active-capture/refund versus passive-callback responsibilities; their implementations
and HTTP response mappings are deliberately deferred. Typed GatewayRequest carries stable shared
transactionId, orderId and validated whole-VND amount; future adapters must verify provider identity,
order linkage, currency and amount before supplying PaymentConfirmation to the core.

Internal PaymentService boundary (not public HTTP):

| Operation | Conditions / result |
| --- | --- |
| begin(orderId,method,amount,content) | Lock order; require PENDING and amount equal to HALF_UP whole-VND order.totalPayment. Reuse the sole matching PENDING attempt; reject another active method/amount or SUCCESS. Persist PENDING otherwise. |
| confirm(proof) | Lock order then transaction; require matching order/method/amount. PENDING->SUCCESS plus order PENDING->PENDING_PROCESSING atomically; publish ORDER_PAYMENT_SUCCEEDED after commit. |
| fail(transactionId) | Only PENDING->FAILED; duplicate/terminal failure updates return false. Used by later creation-failure/expiry orchestration. |
| markRefunded(proof) | Only matching SUCCESS->REFUNDED; duplicate false. Records an already-verified result; never executes a refund or modifies order lifecycle here. |
| find/latest | Shared data only; transactionID, nullable orderId, method, decimal-string amount, nullable transactionContent, status and millisecond UTC createdAt. No order capability/PII. |

Method is an uppercase identifier up to45 characters, supporting future modalities without an enum
schema change. Order existence404; mismatched amount/proof400; incompatible state/active attempt409.
These domain statuses are for future controller mapping, not a new HTTP contract. Rounding is source
whole positive VND using approved BigDecimal/HALF_UP; e.g.132000.50 ->132001. Provider-specific currency
conversion is MODULE9. Source shared-table amount>0, nullable FK, named check/PK/FK/default/precision
and CASCADE delete remain exact. SUCCESS/REFUNDED duplicate confirmation returns false without
resetting order status or republishing. FAILED never revives via a late confirmation.

User approved **freeze delivery while pending/paid and compare payment against order amount**.
PATCH delivery now requires order PENDING; PENDING_PROCESSING and other states return source-style
400 status-transition errors. A PENDING order with shared PENDING/SUCCESS transaction returns409
`Delivery information cannot change while payment is pending or successful`. After a FAILED attempt,
delivery may change and the next attempt must use the newly calculated total. Creation, confirmation,
failure and delivery share the order-row lock; transaction locks always come after the order lock.
The original order detail now queries the real latest SUCCESS method; no absent-table fallback.

Deliberate safety corrections to source: transaction/order confirmation is atomic; only a changed
payment emits the event; duplicates cannot revive cancelled/approved/refunded orders; proofs cannot
cross orders or override amounts; pending attempt reuse prevents duplicate local creation and
conflicting-method attempts. Later adapters must reuse the stable ID for provider idempotency and
resolve/expire an existing attempt before switching methods. These checks alone cannot reconcile
an actual external charge that arrives after a terminal failure; that is a provider integration concern.

PaymentConfirmed carries only orderId/paymentTransactionId and type ORDER_PAYMENT_SUCCEEDED. It is
an in-process event emitted after a successful database commit, not a durable queue or exactly-once
email guarantee. A crash between commit and publish, or failing consumer, requires future delivery/
reconciliation design in MODULE12. Outer transaction rollback emits nothing; provider-specific row
updates must join the same transaction, with order/shared/gateway row lock order. Even a provider
repeat must consult core idempotency rather than skipping core solely because its own row is paid.

Frontend is unchanged from the explicit MODULE7 hash overlay. Core source evidence is the original
PaymentService/PaymentRepository/interfaces/entity/events and tests, with PostgreSQL tests verifying
the intentional state/ownership/concurrency fixes. No live deployment or provider interoperability
is claimed at this checkpoint.

## MODULE 7 order placement and ownership

User explicitly approved **token protection + minimal frontend changes**, superseding public
order access and byte-for-byte frontend preservation for the specific recorded files only.

| Method/path | Access/body | Success |
| --- | --- | --- |
| POST `/api/orders` | Public JSON `{cartItems:[{productId,quantity}],deliveryInfo}` | 201 full persisted order graph including new customerAccessToken |
| GET/HEAD `/api/orders/:orderId` | `x-order-token` for that order, or verified PRODUCT_MANAGER JWT for read only | 200 graph plus paymentMethod; manager response omits customerAccessToken |
| PATCH `/api/orders/:orderId/delivery-info` | `x-order-token` required, complete DeliveryInfo DTO | 200 updated graph; JWT alone cannot authorize edits |
| GET/HEAD `/api/customer/orders/:orderId` | Existing `?token=` capability | 200 graph plus paymentMethod |

Trailing slashes, JSON spellings and Nest envelopes retained. CORS reflects x-order-token in
preflight; all responses/errors have no-store. No cookies/session authentication. Invalid/stale JWT
cannot bypass ownership; a valid order capability works despite an unrelated stale JWT. ADMIN is
not PRODUCT_MANAGER. Missing header token401; wrong/cross-order token404 with the same response
whether or not the order exists. Customer route keeps source missing-query-token400 and wrong-token404.
Noninteger path400; PostgreSQL int32 overflow500. Unexpected failures are sanitized500.
Future pending/refund/list/approve/reject/cancel/payment routes remain403 in this checkpoint.

DeliveryInfo: receiverName/address strings <=255 Unicode code points, province <=100,
email <=255 with email validation, phoneNumber <=20 matching the original Vietnamese expression,
optional nullable string deliveryNotes. Empty names/address/province remain allowed. The original
phone character class permits literal `|`; this source DTO quirk is preserved. Unknown fields are
ignored, including prices/status/token submitted by the client. Original54 offline DTO fixtures
verify whitelist, error ordering, nested messages, numeric/null values, lengths, phone/email cases.
Missing deliveryInfo passes the source DTO but fails500 inside placement; stock is rolled back.
Pathological nested arrays and rare international/quoted email differences between validators are
not claimed fully compatible. Malformed JSON uses stable400 prose instead of Express parser text.

Placement merges duplicates in first-seen order, acquires PostgreSQL product row locks in ascending
ID order, then checks ACTIVE/stock under those locks. Failure400 contains `{message,issues,statusCode}`
with source StockIssue fields. Validated quantities decrement stock and update product timestamps;
order/items/delivery/invoice insert atomically. Any insert/precision/constraint failure rolls back
all changes. PENDING reserves stock immediately; no notification/provider call. No idempotency key
exists in the original contract: repeated successful POSTs create distinct orders/reservations.
Stock release/cancellation and payment expiry remain later modules.

SecureRandom generates32 bytes encoded as64 lowercase hex; existing nullable unique token schema
is preserved. Full response includes orderID/subTotal/tax/shippingFee/totalPayment/status/token/
createdAt/updatedAt, orderItems with orderItemID/quantity/unitPrice and base product fields,
deliveryInfo with deliveryID and nullable notes, invoice with invoiceID/totals/createdAt.
Persisted decimal fields are two-decimal JSON strings; dates are millisecond UTC ISO strings.
Relations omit back-references. Detail adds paymentMethod:null before MODULE8; if its real table
exists, query the latest SUCCESS normally and propagate failures rather than hiding them.

Delivery edits lock the order row and accept PENDING/PENDING_PROCESSING as source; fee uses current
product weights and the order's saved subtotal/tax. They do not reprice items or change stock.
Order, delivery and invoice remain atomic; absent optional delivery/invoice rows can be recreated.
Omitted notes preserve stored notes; explicit null clears them. Other statuses400 with source text.
BigDecimal/HALF_UP follows the user's MODULE6 decision, including0.35 -> VAT0.04.

Frontend changes: two services store the creation capability and attach x-order-token only on
order detail/delivery requests; the new helper uses sessionStorage plus memory fallback. Successful
customer-link lookup remembers the supplied token for subsequent editing in that tab. Closing the
tab/clearing storage requires the original token link; there is no orderId-only recovery. URLs,
UI, payload shapes and provider methods stay unchanged. One obsolete Hello-title boilerplate test
now verifies router navigation. The original70-file source manifest is untouched; an explicit
approved-hash overlay checks67 unchanged originals,3 edited originals and2 additions.
Production API host remains the legacy configured host; this checkpoint is not a deployment.

## MODULE 6 cart and shipping

Both routes are public JSON POSTs (no role, custom header or query required), return201,
accept trailing slashes, ignore stale Authorization headers, and retain global CORS/no-store.
Only these two order paths are opened; placement/detail/delivery stay closed until MODULE7.

| Path | Body | Response |
| --- | --- | --- |
| `/api/orders/cart/check-stock` | `{cartItems:[{productId,quantity}]}` | `{available,issues:[{productId,requestedQuantity,availableQuantity,shortageQuantity,reason}]}` |
| `/api/orders/shipping-fee` | `{province,cartItems:[{productId,quantity}]}` | `{subtotal,tax,shippingFee,totalPayment}`; monetary values are JSON numbers |

IDs/quantities are positive integers with no string coercion; cartItems has at least one element.
Province is a string of at most100 Unicode code points; empty/unknown province is allowed.
Root/nested unknown fields are stripped. Validation400 has Nest `{statusCode,message:[...],error}`;
44 original ValidationPipe fixtures check exact message order, null/type/min/max/Unicode behavior.
Malformed JSON returns400 with a stable sanitized message; exact Express parser prose is not copied.
Source permits some nested-array shapes through validation that fail in its service; arbitrary
malformed nested shapes are not a claim of complete error-precedence parity.

Duplicate IDs merge in first-occurrence order, including stock issues; sums use BigInteger to
avoid int32 overflow. Product IDs outside PostgreSQL int32 produce sanitized500. Beyond JavaScript's
safe-integer range, exact BigInteger/BigDecimal arithmetic is intentional, not IEEE754 emulation.
Stock check requires ACTIVE, treats missing/deleted/inactive as unavailable with availableQuantity0,
and reports insufficient stock separately. It never reserves or writes stock. A later placement
must recheck under row locks, because a successful check is only a snapshot.

Quotes intentionally query all existing statuses and do not reject insufficient stock, matching
source OrderService.calculateShippingFee. Missing IDs produce400 `Some products are not available`.
Only database current_price and weight are used; client prices/dimensions are ignored. Subtotal
sums merged quantities, rounds to2 decimals, VAT is10% rounded to2, total adds rounded VAT and fee.
User explicitly approved **BigDecimal + HALF_UP**: subtotal0.35 gives tax0.04 (legacy JS0.03).
This decimal correction also avoids binary floating point errors at large monetary totals.

Weight-only is the active strategy. Alternative volumetric strategy uses max(actual,L*W*H/6000),
with missing dimensions zero; it is tested but not activated. Calculator clamps negative weights
and normalizes NFD accents, case, dots and JS whitespace. HN/HCM aliases use22000 through3kg;
others30000 through0.5kg; every started extra0.5kg adds2500. Subtotal strictly above100000
subtracts25000, with fee clamped to zero. Exactly100000 has no discount. The472 offline original
shipping fixtures cover aliases, threshold neighbors, half-kilo boundaries and both strategies.
No new database schema, cart persistence, order stubs, notifications or provider calls.

## MODULE 5 product administration

Six routes now require JWT PRODUCT_MANAGER: POST `/api/products`, PATCH `/api/products/:id`,
PATCH `/api/products/:id/stock`, POST `/api/products/batch-delete`, POST
`/api/products/batch-deactivate`, GET `/api/products/audit-logs`. POST201, PATCH/GET200; trailing
slashes supported. ADMIN alone is insufficient. Public search/random/detail still ignore stale JWT.
The x-manager-id header remains required/nonblank with the original400 message. **User-approved
change:** the signed JWT email identifies audit/quota; submitted header text cannot change identity.

Create/update whitelist common fields and nested BOOK/CD/DVD/NEWSPAPER DTOs; 11 original Nest
ValidationPipe fixtures verify missing fields, numeric strings, minima, date/nested errors, null
optionals and unknown-field stripping. Product type uppercases; originalPrice/type cannot change.
Prices use BigDecimal with inclusive30%–150% bounds; stock must stay a nonnegative integer.
Subtype media aliases, nulls, two-decimal numeric strings and date JSON reuse the verified public
catalog serializer. Partial nested updates preserve omitted fields. Supplied CD tracks replace all
tracks (empty array clears; absent tracks preserves). SQL lengths/unique/FKs still reject invalid
persistence with the generic500 envelope. Mutations and audit are transactional.

Batch IDs must be unique, 1–10 integers. Results retain request order and NOT_FOUND entries.
Delete with positive stock→DEACTIVATED; zero stock with order reference→DEACTIVATED_ORDERED;
otherwise soft DELETED. Deactivation always uses DEACTIVATED. DELETE/DEACTIVATE each consume one
quota unit per found product, including repeated deactivation; maximum20 per manager/day using
server-local day boundaries. PostgreSQL advisory locks serialize the signed manager's quota checks;
sorted product row locks protect overlapping batches. Stock deltas use a row lock too.
Audit created_at is explicitly stamped after locking with clock_timestamp(), avoiding charging an
operation to a prior day solely because its transaction started before a midnight lock wait.

`order_items` is read only if public.order_items exists; absent table at this pre-MODULE7 checkpoint
means there can be no persisted order references. No future-domain table or business stub is created.
A test-only table fixture verifies ordered-product behavior. MODULE7 must retest against its full DDL.
Audit GET returns all logs newest-first with JSONB changes, nullable reason/product, source-shaped
base product response and millisecond UTC createdAt. Physical deletion keeps audit via SET NULL.

Source behavior retained: supplied DELETED status can commit a create/update then return404 from
the post-transaction detail lookup; stock adjustment also locates soft-deleted products before that
lookup. General status strings remain unconstrained. No extra undelete/activation business rule.
Compatibility limits: malformed nested arrays/null required subtype become controlled400 rather
than source incidental errors; Java ISO date parsing is strict, so permissive validator.js date
edge cases outside the captured fixtures are not certified. Duplicate-ID validation runs before
the controller's manager-header check; both failures are400 but error precedence differs if both
are invalid. All ordinary Angular requests retain their paths/methods/headers/payload/response.

## MODULE 4 user administration

JWT + exact ADMIN now protects GET/POST `/api/users`, GET `/api/users/logs`, PATCH
`/api/users/:userId`, PATCH `/:userId/status`, PATCH `/:userId/roles`, POST
`/api/users/:userId/reset-password` and POST `/api/auth/reset-password/:userId`.
POST returns201; GET/PATCH200. Trailing slashes are accepted. Missing JWT401, other roles403.
Attribution comes from signed JWT email, never the submitted body. CSRF excludes these stateless
API routes only; existing CORS/no-store behavior applies to errors and reset responses too.

Create validates email, nonempty string fullName/phoneNumber, password>=6 Unicode characters and
nonempty string roles array; unknown fields are ignored. Source actually requires phoneNumber
despite Angular's optional TypeScript declaration. Error array for an empty body was captured from
original class-validator and tested exactly. Whitespace-only names/phones are accepted as source
IsNotEmpty does; database lengths/nullability remain unchanged. Invalid role names and duplicate
email preserve source400 text. Case-sensitive uniqueness and role names are retained.

List sorts by userID ASC; each user has latest10 logs by createdAt DESC. Global logs return all
logs newest-first, with nullable user after deletion. User/role fields, null phone, audit fields and
millisecond-UTC timestamps are explicit response maps; nested audit users omit unloaded roles,
and per-user audit entries omit unloaded user. PasswordHash is excluded from all responses,
including create: an intentional fix for R10, not exact reproduction of credential disclosure.

Profile PATCH only changes email/fullName/phoneNumber. Status aliases BLOCKED→DEACTIVATED and
UNBLOCKED→ACTIVE remain accepted. Roles are replaced after validation and duplicates collapse.
All mutations and audit inserts share a transaction; audit failure rolls back account changes.
Reset via `/api/users` returns `{temporaryPassword}` (12 secure alphanumeric characters); reset
via `/api/auth` returns `{success,message,newPassword,email,fullName}` (8 uppercase hex characters).
Both preserve original audit action/descriptions and BCrypt cost10. No automatic user seed.

Compatibility limits: user IDs beyond int32 return controlled400 rather than a legacy DB500;
malformed nonstring roles are controlled400. The 12-character reset generator guarantees length,
where source base64 filtering can rarely produce fewer characters. Email validation combines
Jakarta Email with the source-required TLD/length restrictions; exhaustive validator.js edge-case
equivalence is not claimed. Existing-token revocation/self-admin protection are not added.

## MODULE 3 authentication

Implemented POST `/api/auth/login` (public, `{email,password}`) and POST
`/api/auth/change-password` (Bearer JWT, `{oldPassword,newPassword}`); success status201 as in Nest.
Login returns `{token,user:{userID,email,fullName,roles:string[]}}`. HS256 token has the same user
claims plus iat/exp with24h lifetime. UTF-8 signing secret comes exclusively from JWT_SECRET
(minimum32 bytes). Missing/short secret prevents startup; local setup generates a random ignored key.
Nimbus is supplied through Spring Security's managed oauth2-jose dependency; no custom JWT crypto.

Login errors retain the source401 messages for incorrect credentials and deactivated accounts.
Change-password validates trimmed UTF-16 length>=6, hashes the original untrimmed value with BCrypt
cost10, and commits the credential and CHANGE_PASSWORD audit in one transaction. Returns
`{success:true,message:"Đổi mật khẩu thành công"}`; short new password/wrong old password return400,
absent user404. Existing source bcryptjs hashes and UTF-8 72-byte truncation remain supported.
JWT errors distinguish missing/wrong-case Bearer prefix from invalid/expired token with source401
envelopes. Exact role authorities support hasAnyAuthority; ADMIN does not imply PRODUCT_MANAGER.
Role-protected business endpoints and reset-password routes remain closed until MODULE4/5.

CORS now applies globally with the original localhost/Vercel/ALLOWED_ORIGINS allow rules,
OPTIONS204 and reflected requested headers, without allow-credentials. API responses have no-store.
Public catalog/login ignore stale Bearer tokens. CSRF is excluded only on these stateless auth POST
routes; session/cookie login is not introduced. Unknown/unimplemented routes remain denied.

Deliberate input hardening: absent/nonstring credentials produce controlled401/400 rather than
legacy incidental bcrypt/TypeError500. JWT verification requires HS256, expiry, positive integer
userID and a string role array; malformed claims and unsupported algorithms are rejected. These
checks preserve tokens issued by source login, not arbitrary JWTs accepted by its permissive guard.
No token revocation is added: existing tokens remain usable after password/status/role changes until
expiry, matching the original guard. Admin authorization/mutation behavior remains a later decision.

## MODULE 2 domain boundary

Flyway V3 adds `users`, `roles`, `users_roles`, `user_audit_logs`. Original TypeORM metadata
is captured offline by `tools/capture-user-schema.cjs` and replayed into a separate PostgreSQL
schema for comparison of columns/defaults, named constraints and indexes. No deployed DB was read.

User lookups preserve case-sensitive email matching, integer `userID`/`roleID`, nullable phone,
string status and multiple roles. Audit attribution remains nullable varchar(50), timestamps are
Instant/timestamptz, and deleting a user removes memberships while retaining logs with null user.
Shared roles are not removed by user deletion; deleting a referenced role is rejected by PostgreSQL.
JPA timestamps are maintained on entity insert/update; native SQL callers must update `updated_at`
explicitly, as in the original schema which has no timestamp trigger.

This module exposes no new HTTP route. The existing `/api/users` inventory below remains a contract
for MODULE4 (JWT + ADMIN, with authorization supplied by MODULE3). Login/password APIs also remain
pending MODULE3. User entities are internal: JPA loads passwordHash, but Jackson ignores it; this
does not yet certify admin response parity or implement the unsafe legacy create response (R10).
Future HTTP DTOs must deliberately preserve response spelling/null/date behavior without credentials.
Role-only versioned seeding installs ADMIN/PRODUCT_MANAGER/STAFF and never provisions default users
or rewrites passwords, status or memberships. Password hashing and JWT are outside this checkpoint.

## Evidence map

All paths below are relative to the source repository; file contents are pinned by the source commit.

- HTTP/bootstrap: `src/backend/src/main.ts`, `app.controller.ts`, `app.service.ts`, `app.module.ts`.
- Authentication: `src/backend/src/auth/{auth.controller,auth.service,jwt-auth.guard,roles.guard}.ts`.
- Users: `src/backend/src/user/user-admin.controller.ts`, `services/user-admin.service.ts`,
  `entities/*`, `dto/create-user.dto.ts`, `user.module.ts`.
- Products: `src/backend/src/product/product.controller.ts`, `product.service.ts`,
  `product.repository.ts`, `dto/*`, `entities/*`, `handlers/*`, `validators/*`.
- Orders/cart: `src/backend/src/order/{order,customer-order}.controller.ts`, `order.repository.ts`,
  `services/*`, `strategies/*`, `dto/*`, `entities/*`.
- Payments: `src/backend/src/payment/controllers/*`, `services/*`, `API/*`, `dto/*`,
  `entities/*`, `interfaces/payment-qrcode.interface.ts`.
- Angular: `src/frontend/src/app/app.config.ts`, `app.routes.ts`, `services/*.ts`,
  `interceptors/auth.interceptor.ts`, `guards/auth.guard.ts`; customer checkout/cart/product-detail/
  payment/payment-result/invoice/order-detail, admin users/logs and pm products/orders/logs components.

## Shared HTTP rules

- Default local backend: `http://localhost:3000` (`PORT` override). There is no global API prefix;
  controllers explicitly include `/api`, while merchant routes start `/vqr` and greeting uses `/`.
- Angular `API_BASE_URL`: hostname `localhost` or `127.0.0.1` → `http://localhost:3000`;
  otherwise → `https://isd-20252-25.onrender.com`. No trailing slash or frontend environment override.
- Requests with bodies are JSON (`Content-Type: application/json`). Response JSON has no global
  wrapper. Greeting is text. GET/PATCH success is 200; **every POST below returns 201** because no
  controller overrides Nest's default status. Framework-handled OPTIONS is normally 204.
- Nest/Express ETag is disabled. Every `/api` request gets `Cache-Control: no-store` middleware.
- CORS accepts no Origin, `http://localhost:<digits>`, `http://127.0.0.1:<digits>`,
  `http://0.0.0.0:<digits>`, `https://*.vercel.app`, and exact trimmed comma-separated
  `ALLOWED_ORIGINS` entries. Accepted origins are reflected. Default methods are
  GET,HEAD,PUT,PATCH,POST,DELETE; requested headers are reflected; credentials are not enabled.
  An unaccepted origin gets no allow-origin header (the source does not reject the normal request).
- Angular attaches `Authorization: Bearer <aims_token>` if localStorage contains the token,
  even on public routes. PRODUCT_MANAGER additionally gets `x-manager-id: <JWT email>`.
- No cookie/session authentication. JWT claims: `userID` number, `email`, `fullName`, `roles`
  array of strings, `iat` and `exp` in epoch seconds. Source signing uses jsonwebtoken's default
  HS256 and 24h expiry. Java must preserve claims; do not copy the insecure secret fallback.
- JWT guard requires case-sensitive `Bearer `; missing/malformed/expired token → 401.
  Role guard requires at least one declared role, exact case, otherwise 403. ADMIN does not
  implicitly grant PRODUCT_MANAGER. Customer order token is separate from JWT.
- Public below means no JWT/role guard in existing code. It is not an endorsement of access policy.

## Error conventions

| Symbol | HTTP / response / behavior |
| --- | --- |
| V | 400 DTO validation: `{message: string[], error: "Bad Request", statusCode: 400}`. Product/user/order/VietQR controllers use whitelist + transform; PayPal whitelist without transform. Unknown DTO fields are removed. No global ValidationPipe. `any`/interface bodies do not get class DTO validation. |
| I | 400 invalid integer path/page/limit: message `Validation failed (numeric string is expected)` in standard Bad Request envelope. ParseIntPipe does not itself require positive integers. |
| B | 400 business error: `{message: string, error: "Bad Request", statusCode: 400}` unless a custom object is explicitly thrown. Keep spelling/localization. |
| N | 404: `{message: string, error: "Not Found", statusCode: 404}`. |
| A | 401: `{message: string, error: "Unauthorized", statusCode: 401}`. JWT messages: `Không có quyền truy cập: Token thiếu hoặc không hợp lệ` or `Không có quyền truy cập: Phiên đăng nhập đã hết hạn hoặc không hợp lệ`. |
| R | 403: `{message: "Bạn không có quyền truy cập chức năng này", error: "Forbidden", statusCode: 403}`. |
| S | 400 custom stock error: `{message: "Some products do not have enough stock", issues: StockIssue[]}`. Nest passes this explicit object through; do not add mandatory statusCode/error fields. |
| U | Unexpected database/runtime errors normally become `{statusCode: 500, message: "Internal server error"}`. No global custom exception filter was found. |

All routes may encounter U; tables list route-specific expected errors. Guard checks occur before
DTO pipes. Validation and query parsing edge cases must be frozen with module tests before porting;
do not replace this envelope with Spring ProblemDetail or add global validation indiscriminately.

## Payload dictionary

Notation: `?` means optional; `| null` means present null is possible; arrays have no envelope unless
shown. Identifiers are numbers except provider IDs/token/ref strings. Preserve acronyms and casing.

- **ProductBase**: `productID`, `productType`, `title`, `category`, `description|null`, `barcode`,
  `length|null`, `width|null`, `height|null`, `weight`, `originalPrice`, `currentPrice`,
  `quantityInStock`, `status`, `imageUrl|null`, `createdAt`, `updatedAt`.
- **ProductDetail**: ProductBase plus exactly the applicable `book`, `cd`, `dvd` or `newspaper`
  property (null if missing subtype). Lists return base products without loading subtype detail.
  Detail flattens `media` and removes back-reference `product`; does not emit the media object.
  Common flattened fields: `productID`, `publisher|null`, `releaseDate|null`, `language|null`,
  `genre|null`, plus aliases `publicationDate=releaseDate`, `recordLabel=publisher`, `studio=publisher`
  whenever the original field is defined, including null. Keep these extra aliases for every subtype.
  Book adds `authors`, `coverType`, `numPages|null`; CD adds `artists`, `tracks:[{id,title,lengthSeconds}]`;
  DVD adds `discType`, `director`, `runtimeMinutes`, `subtitles`; newspaper adds `editorInChief`,
  `issueNumber|null`, `frequency|null`, `issn|null`, `sections|null`.
- **ProductInput**: required `productType,title,category,barcode` strings, numeric
  `weight,originalPrice,currentPrice`, integer `quantityInStock>=0`; optional
  `description,length,width,height,status,imageUrl`, matching subtype object.
  Book input: required `authors,coverType,publisher,publicationDate` (ISO date), optional
  `numPages>=1,language,genre`. CD: `artists,recordLabel,genre`, optional `releaseDate,tracks`
  where track has `title` and integer `lengthSeconds>=1`. DVD: `discType,director,runtimeMinutes>=1,
  studio,language,subtitles`, optional `releaseDate,genre`. Newspaper:
  `editorInChief,publisher,publicationDate`, optional `issueNumber,frequency,issn,language,sections`.
  Update DTO makes fields optional; business validation forbids changing product type or originalPrice.
  Creation requires exactly one matching subtype; weight/originalPrice and provided dimensions
  must be positive; currentPrice must be between 30% and 150% of originalPrice.
- **ProductLog**: `logID,actionType,changedFields|null,performedBy,reason|null,createdAt,product|null`.
- **User**: `userID,email,fullName,phoneNumber|null,status,createdAt,updatedAt,roles` where roles are
  `{roleID,name}` objects. List appends `auditLogs`; login/JWT instead has `roles:string[]`.
  List is userID ASC with the newest 10 auditLogs per user (these nested logs do not load user).
  Source saved creation entity may include `passwordHash` (risk R10; never silently reproduce).
- **UserLog**: `logID,action,description|null,performedBy|null,createdAt,user` where loaded user
  contains its selected fields; absent/deleted FK can be null. Log list is newest-first.
- **CartItem**: `{productId: positive integer, quantity: positive integer}`; `cartItems` nonempty.
  Both stock check and placement merge duplicate product IDs by summing quantities.
- **StockIssue**: `productId,requestedQuantity,availableQuantity,shortageQuantity,reason` where reason
  is `PRODUCT_NOT_AVAILABLE` or `INSUFFICIENT_STOCK`.
- **Delivery** input: required `receiverName` string <=255, email <=255, `phoneNumber` <=20 matching
  source regex `^(0|\+84)[3|5|7|8|9][0-9]{8}$`, `address` <=255, `province` <=100;
  optional string `deliveryNotes`. Response adds `deliveryID`, nullable notes, omits unloaded order.
  Preserve Vietnamese custom validation messages for email/phone.
- **Order**: `orderID,subTotal,tax,shippingFee,totalPayment,status,customerAccessToken|null,createdAt,
  updatedAt,orderItems,deliveryInfo,invoice`. Loaded `orderItems` are `{orderItemID,quantity,unitPrice,
  product:ProductBase}`; invoice is `{invoiceID,totalExcludeVAT,totalIncludeVAT,shippingFee,totalPayment,
  createdAt}`. Detail adds `paymentMethod` (`PAYPAL`, `VIETQR`, or null) from latest SUCCESS payment.
  Mutation endpoints return reloaded Order without promising that added paymentMethod field.
- **OrderPage**: `{items:[Order + paymentMethod],total,page,limit,totalPages}`; no alternative `data` field.
- **QrPayment**: `{paymentId,orderId,amount,transactionRef|null,content,paymentContent,qrCode|null,
  qrLink|null,expiredAt,status,bankCode,bankAccount,bankAccountName}`. status is
  `PENDING|PAID|EXPIRED|FAILED`. `paymentId` is shared payment transaction ID, not VietQR row ID.
- **QrCallback**: required `bankaccount:string,amount:positive number,transType:"C"|"D",content:string,
  transactionid:string,transactiontime:number,referencenumber:string,orderId:string`;
  optional `terminalCode:string,sign:string`. transactiontime is interpreted as epoch milliseconds.

### Numbers, nulls and dates

PostgreSQL decimal(12,2) has no TypeORM numeric transformer in these entities. Loaded numeric
columns normally serialize as JSON strings (`"125000.00"`); explicitly computed shipping totals,
QR `amount`, integer identifiers/counts and stock serialize as JSON numbers. Angular uses
`number|string` for money and `Number(...)` conversions. Java should use BigDecimal internally
with per-response Jackson serialization; do not globally rename fields or normalize all decimals.
Date columns such as releaseDate are date-only strings (`YYYY-MM-DD`); JS Date/timestamptz columns
serialize as UTC ISO-8601 strings with milliseconds. These runtime serialization details are
source-derived expectations pending isolated legacy fixtures, not captured production responses.

## Product routes

All paths below start `/api/products`. PM means Bearer JWT + PRODUCT_MANAGER and mandatory
nonblank `x-manager-id`; header is trimmed, missing gives B `x-manager-id header is required`.

| Method / path | Auth / role / additional headers | Query or body | Success | Errors / behavior |
| --- | --- | --- | --- | --- |
| GET `/api/products` | Public | Query `keyword?,category?,mediaTypes?,minPrice?,maxPrice?,status?`; no body | 200 ProductBase[] | B for nonfinite prices (`minPrice must be a number` / maxPrice) or `Invalid media type: X`. Empty price ignored; CSV types trimmed/uppercased/deduplicated; BOOK,CD,DVD,NEWSPAPER only. |
| GET `/api/products/random` | Public | None | 200 ProductBase[] | At most 20 ACTIVE products, RANDOM(); no limit query consumed. |
| GET `/api/products/:id` | Public | Integer id | 200 ProductDetail | I; N `Product with ID X not found`; DELETED excluded, DEACTIVATED accessible. |
| GET `/api/products/audit-logs` | PM | None | 200 ProductLog[] | A/R/B; all logs newest-first, not scoped to manager. |
| POST `/api/products` | PM | ProductInput | 201 ProductDetail | A/R/V/B; required subtype/business price rules; duplicate barcode may reach U. |
| PATCH `/api/products/:id` | PM | Partial ProductInput | 200 ProductDetail | A/R/I/V/B/N; cannot change productType. |
| PATCH `/api/products/:id/stock` | PM | `{quantityDelta:integer,reason:string(min length 1)}` | 200 ProductDetail | A/R/I/V/N; B `Stock adjustment cannot make stock negative`; pessimistic write lock. |
| POST `/api/products/batch-delete` | PM | `{ids:integer[1..10]}` | 201 `{results:[{id,status}]}` | A/R/V/B; ids unique. status DEACTIVATED when stock>0, DEACTIVATED_ORDERED when sold before, DELETED soft-delete otherwise, NOT_FOUND for absent/deleted. |
| POST `/api/products/batch-deactivate` | PM | `{ids:integer[1..10]}` | 201 `{results:[{id,status}]}` | A/R/V/B; DEACTIVATED or NOT_FOUND; no hard delete. |

Search is DISTINCT and title ASC. Default status ACTIVE; ALL excludes DELETED; explicit other
status is uppercased (even DELETED). Keyword ILIKE searches base and subtype/media/track text.
Category aliases include SÁCH/SACH/BOOK, BÁO/BÁO CHÍ/BAO/BAO CHI/NEWSPAPER plus CD/DVD;
category matches either alias or productType. Price bounds are inclusive; no pagination/min<=max
validation in controller. Batches count existing products against 20 manager delete/deactivate
actions per server-local day, with B `ids must be unique` or
`Manager delete quota exceeded: maximum 20 products per day`.

Angular consumers: ProductService and home/product-detail/pm products/logs components. Manager
header is supplied only by interceptor; service methods themselves do not add it.

## Authentication and user routes

| Method / path | Auth / role | Body / query | Success | Errors / behavior |
| --- | --- | --- | --- | --- |
| POST `/api/auth/login` | Public | `{email,password}` (any body, no DTO pipe) | 201 `{token,user:{userID,email,fullName,roles:string[]}}` | A `Tài khoản hoặc mật khẩu không chính xác` or `Tài khoản đã bị vô hiệu hóa hoặc khóa`; malformed input may U. |
| POST `/api/auth/change-password` | Bearer, any role | `{oldPassword,newPassword}` | 201 `{success:true,message:"Đổi mật khẩu thành công"}` | A; B new trimmed length<6 (`Mật khẩu mới phải có ít nhất 6 ký tự`) or wrong old password (`Mật khẩu cũ không chính xác`); N missing user. |
| POST `/api/auth/reset-password/:userId` | Bearer, ADMIN | Integer userId, body ignored (`{}` from Angular) | 201 `{success:true,message:"Reset mật khẩu thành công",newPassword,email,fullName}` | A/R/I; N `Không tìm thấy người dùng cần reset mật khẩu`; 8 uppercase hex-character generated password. |
| GET `/api/users` | Bearer, ADMIN | None | 200 User[] + per-user auditLogs | A/R; no pagination. |
| POST `/api/users` | Bearer, ADMIN | `{email,fullName,phoneNumber,password,roles:string[]}` | 201 User (see R10) | A/R/V/B duplicate email / invalid role. phoneNumber required in DTO although optional in Angular interface; password min6; roles nonempty. |
| GET `/api/users/logs` | Bearer, ADMIN | None | 200 UserLog[] | A/R. |
| PATCH `/api/users/:userId` | Bearer, ADMIN | `{email?,fullName?,phoneNumber?}` interface | 200 User | A/R/I/N; source does not class-validate interface; duplicate email may U. No Angular service caller found. |
| PATCH `/api/users/:userId/status` | Bearer, ADMIN | `{status}` | 200 User | A/R/I/N/B `Trạng thái không hợp lệ`; ACTIVE/UNBLOCKED→ACTIVE, DEACTIVATED/BLOCKED→DEACTIVATED (case sensitive). |
| POST `/api/users/:userId/reset-password` | Bearer, ADMIN | Integer id, ignored body | 201 `{temporaryPassword}` | A/R/I/N; generated length12; differs from auth reset route. No Angular caller found. |
| PATCH `/api/users/:userId/roles` | Bearer, ADMIN | `{roles:string[]}` | 200 User | A/R/I/N/B empty list or unknown roles. |

No query parameters are consumed by these routes. User audit `performedBy` comes from JWT email,
not x-manager-id. Source passwords use bcrypt cost10. Angular stores `aims_token`, decodes exp,
removes expired token, and routes ADMIN to `/admin/*`, PRODUCT_MANAGER to `/pm/*`; no refresh/logout
backend endpoint exists. Login/change-password/user forms render `error.message`, joining arrays.

## Cart, order and customer access routes

Unless listed, no additional headers beyond JSON/optional Angular Bearer. PM here requires
JWT + PRODUCT_MANAGER but does NOT require x-manager-id.

| Method / path | Auth / role | Query or body | Success | Errors / behavior |
| --- | --- | --- | --- | --- |
| POST `/api/orders/cart/check-stock` | Public | `{cartItems:CartItem[]}` | 201 `{available:boolean,issues:StockIssue[]}` | V; unavailable stock is a success response with available=false. Stateless. |
| POST `/api/orders/shipping-fee` | Public | `{cartItems:CartItem[],province}` | 201 `{subtotal,tax,shippingFee,totalPayment}` numbers | V/B `Some products are not available` if IDs absent; does not check ACTIVE/stock. Note lowercase `subtotal` vs Order.subTotal. |
| POST `/api/orders` | Public | `{cartItems:CartItem[],deliveryInfo:Delivery}` | 201 Order | V/S; merge duplicate items, lock products, reserve stock and persist order/items/delivery/invoice in one transaction. |
| GET `/api/orders/pending` | PM | `page=1,limit=30,search?,dateRange?,paymentMethod?` | 200 OrderPage | A/R/I; page clamped>=1, limit clamped1..30. |
| GET `/api/orders/vietqr-refunds` | PM | Same query | 200 OrderPage | A/R/I; REFUND_PENDING status list; forces non-VIETQR method filter to ALL, fallback paymentMethod VIETQR. |
| GET `/api/orders/:orderId` | Public | Integer id | 200 Order + paymentMethod | I/N `Order with ID X not found`; includes customerAccessToken and delivery data (R04). |
| PATCH `/api/orders/:orderId/delivery-info` | Public | Delivery | 200 Order | I/V/N/B if status not PENDING/PENDING_PROCESSING; recalculates shipping and invoice. |
| POST `/api/orders/:orderId/approve` | PM | Body ignored (`{}`) | 201 Order | A/R/I/N/B invalid transition; PENDING/PENDING_PROCESSING→APPROVED, even unpaid. |
| POST `/api/orders/:orderId/reject` | PM | Body ignored (`{}`) | 201 Order | A/R/I/N/B; restore stock, automatic PayPal refund where applicable, REJECTED or REFUND_PENDING. |
| POST `/api/orders/:orderId/cancel` | PM | Body ignored (`{}`) | 201 Order | A/R/I/N/B; restore stock, CANCELLED or REFUND_PENDING. Tokenless customer caller currently conflicts with guard. |
| POST `/api/orders/:orderId/confirm-vietqr-refund` | PM | Body ignored (`{}`) | 201 Order | A/R/I/N/B unless REFUND_PENDING with SUCCESS VietQR transaction; marks transaction and order REFUNDED. |
| GET `/api/customer/orders/:orderId` | No JWT; customer token | Query `token` required | 200 Order + paymentMethod | I; B `Missing customer order access token`; N `Order with ID X was not found for this access token`. |
| POST `/api/customer/orders/:orderId/cancel` | No JWT; customer token | Query `token` required; ignored body | 201 Order | I/B/N; same cancellation rules/refund effects after token lookup. |

Token is 32 cryptographically random bytes encoded as 64 hex characters, nullable/unique in DB;
lookup matches order ID and exact token. Whitespace is checked for emptiness but not trimmed for
lookup. No token expiry exists in source. Angular URL-encodes the token query parameter.

List filters: search trimmed, optional `#` numeric order ID OR receiver/email/phone text; dateRange
ALL/TODAY/WEEK/MONTH, unknown values do not filter; WEEK starts local midnight six days ago,
MONTH first of current month. paymentMethod ALL/UNPAID or exact gateway method; latest SUCCESS
payment determines it. Pending order sort: status DESC then createdAt ASC; refund list createdAt ASC.

Order transition error: `Order X cannot <verb> from status Y`, with verbs `update delivery info`,
`be approved`, `be rejected`, `be cancelled`. Mutation statuses and refund effects need module 11
state-table and concurrent tests; no change to the frontend's existing messages/expectations.

Shipping: weight-only strategy currently wired, total weight sums product.weight*quantity;
VAT 10% of subtotal, shipping untaxed, money rounded to two decimals. Ha Noi/HCM aliases normalize
diacritics/case/dots/spaces: 22,000 for <=3kg, then 2,500 per started0.5kg. Other provinces:
30,000 for <=0.5kg, then same increment. Subtotal **>100,000** gets max25,000 shipping discount,
floor zero. A volumetric strategy exists but current calls do not supply dimensions.

Angular consumers: OrderService, PaymentService, cart/checkout/invoice/payment/order-detail,
pm orders. Checkout distinguishes 400 stock `issues` from ordinary string/array messages.

## PayPal routes and redirect contract

All three routes are public in legacy controller; no auth/ownership guard. Body is JSON, no queries.

| Method / path | Body | Success | Errors / behavior |
| --- | --- | --- | --- |
| POST `/api/paypal/order/create` | `{orderID:positive number}` | 201 `{paypalOrderID,status,approveUrl?}` | V/N missing order; B gateway/credential failure. Rounds VND total to integer, divides by25000, formats USD to2 decimals. |
| POST `/api/paypal/order/capture` | `{paypalOrderID:nonempty string,orderID:positive number}` | 201 raw PayPal capture JSON (e.g. id,status,purchase_units,payer,links) | V/B gateway failures. COMPLETED updates payment/order; other successful gateway status still returns raw201. Frontend treats HTTP success as success. |
| POST `/api/paypal/order/refund` | `{orderID:positive number}` | 201 raw PayPal refund JSON | V/N `No transaction found for order X`; B absent successful capture / gateway error. Changes payment record, not order status. |

`APP_PUBLIC_URL` defaults `http://localhost:4200`, trailing slash removed. Gateway order uses
return_url `<frontend>/payment?orderId=X&success=true` and cancel_url
`<frontend>/payment?orderId=X&cancel=true`. PayPal adds `token` (gateway order ID). Angular redirects
browser to approveUrl, reads token/success/cancel, calls capture and then `/payment-result`.
That gateway token must never be confused with customerAccessToken. PayPal client uses OAuth
Basic credentials + form grant_type=client_credentials and Bearer API calls; default is sandbox.

## VietQR and merchant routes

| Method / path | Auth / headers | Query or body | Success | Errors / behavior |
| --- | --- | --- | --- | --- |
| POST `/api/vietqr/payments` | Public, JSON | `{orderId:positive int,amount:positive number,content:string}` | 201 QrPayment | V/B; orderId and amount coerce via Number; content 1..23 ASCII alphanumeric/spaces, trimmed uppercase; amount rounded integer; reuse pending matching order/amount. Missing DB order may U (FK). |
| GET `/api/vietqr/payments/:paymentId/status` | Public | Integer paymentId, no body | 200 QrPayment | I/N `VietQR payment X was not found`; expires pending payment at deadline and marks shared transaction FAILED. |
| GET `/api/vietqr/payments/by-ref/:transactionRef/status` | Public | Reference string | 200 QrPayment | N `VietQR transaction reference X was not found`; same expiry behavior. No Angular caller found. |
| POST `/api/vietqr/payments/:paymentId/trigger-callback` | Public, JSON | Integer id, ignored `{}` | 201 `{status:"SUCCESS"}` | I/N/B unless pending and API base contains dev.vietqr.org; calls gateway test endpoint. |
| POST `/api/vietqr/payments/callback` | Public, JSON | QrCallback | 201 `{status:"SUCCESS",message,paymentId}` | V/B/N; message `Callback processed` or `Callback already processed`. |
| POST `/vqr/api/token_generate` | `Authorization: Basic base64(username:password)` | Body ignored | 201 `{access_token:UUID,token_type:"Bearer",expires_in:300}` | A `Invalid VietQR merchant credentials`; compares VIETQR_MERCHANT_USERNAME/PASSWORD. |
| POST `/vqr/bank/api/transaction-callback` | No validated auth/signature, JSON | Merchant payload mapped to QrCallback | 201 callback result as above | B/N/U; no DTO validation pipe in this controller. |
| POST `/vqr/bank/api/transaction-sync` | Same | Same (sandbox merchant registered URL) | 201 callback result as above | Same. |

Merchant aliases: bankaccount/bankAccount; transactionid/transactionId;
transactiontime/transactionTime; referencenumber/referenceNumber; orderId/orderid.
String fields use String(value ?? ''), numbers use Number(value), terminalCode/sign remain optional.
Callback target lookup: reference first, then content+order+amount, then content+amount. It accepts
only credit transType C; validates order when parsed>0, rounded amount, trimmed uppercase content;
PAID retries return already-processed; other non-PENDING states fail. `sign` is not verified (R06).
Raw JSONB storage sanitizes away bankaccount/sign. Expiry defaults15 minutes, fallback15 for invalid
TTL configuration; expiry is applied during GET status, not automatically by a scheduler.

Gateway calls use `VIETQR_API_BASE_URL` default `https://dev.vietqr.org`, token endpoint
`/vqr/api/token_generate`, generation `/vqr/api/qr/generate-customer`, and test callback
`/vqr/bank/api/test/transaction-callback`. Merchant callback/sync URLs must be registered with the
backend public base; source does not generate a callback URL in the QR request.

Angular payment uses content `AIMS <orderId>`, polls status every5 seconds and parses expiredAt
as Date. It reads paymentContent/bank fields, renders qrCode (raw EMV/base64/URL), ignores qrLink,
and offers the sandbox trigger button. Preserve response spellings `paymentId`, `transactionRef`,
`orderId` here; do not substitute `transactionID`, `transactionRefId` or `orderID`.

## Root and Phase 0 addition

| Method / path | Auth / query / body | Success | Migration status |
| --- | --- | --- | --- |
| GET `/` | Public; none | 200 text `Hello World!` | Existing Nest greeting; no Angular caller; unimplemented in Phase 0. |
| GET `/actuator/health` | Public; none | 200 `{status:"UP"}`, 503 when aggregate health DOWN | New foundation endpoint, not a legacy route. No health components/details exposed. |

There are 44 existing controller route declarations in this inventory (including greeting and
merchant aliases), plus the new health endpoint. No backend cart persistence, token refresh,
logout, user DELETE or notification HTTP endpoint exists. All business APIs remain unimplemented
in Phase 0; their compatibility assertions must be added in the corresponding module.

## MODULE 12 — notification side effects (no new HTTP API)

Payment success and order approve/reject/cancel keep their existing methods, paths, authentication,
roles, payloads, responses and status codes. After a real state change, the same business transaction
stores one EMAIL snapshot per event/refund status. Duplicate state callbacks do not send again.
Delivery happens separately; provider outages leave the successful business response unchanged.
Four source email subjects/text variants and order/invoice/payment/refund fields are retained;
view links use `/order-detail?orderId=...&token=...`. The payment email has no customer Cancel button,
consistent with the user-approved PM-only paid cancellation policy. Angular is unchanged.
Source in-memory best-effort delivery is replaced by a durable V10 outbox with bounded retries.
Default local delivery is off; pending notifications accumulate until configuration enables sending.
See [Module12 validation/configuration](module-12-validation.md) for retry/PII/delivery limits.

## MODULE 13 — integration checkpoint

No endpoint, method, header, JSON, role or frontend change. Full prior contract suite plus three
production-profile journeys verify catalog→stock→placement→owned QR→merchant callback→PM lifecycle
and notification composition. Local Docker smoke checks actual HTTP health/catalog and PM protection
on a fresh V1–V10 PostgreSQL database. Existing policy exceptions remain those explicitly approved
in Modules5–11. No claim of live provider or unknown production-schema equivalence is made.
See [validation](module-13-validation.md) and [deployment/URL constraints](deployment.md).
