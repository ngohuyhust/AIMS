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
