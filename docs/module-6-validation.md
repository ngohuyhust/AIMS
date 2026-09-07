# MODULE 6 validation — Cart and Shipping

Source: `c7c022e33f100937cd0f072c3666fd0e26754d8e`, read-only sibling ISD.20252-25.
Authorization: finish MODULE5 and continue MODULE6 in the same request. MODULE5 completion
`5dff092e25b79348a5b2cb0d38d79ad75a87fdf7` was already pushed and verified before these changes.

Read Nest order controller, cart/shipping DTOs, CartService, OrderService.calculateShippingFee,
ShippingCalculatorService, both strategies and their module binding; Angular order/cart services.
No source AppModule/bootstrap/environment was loaded. No source files or Git state were changed.

## Scope and decisions

- Public POST201 `/api/orders/cart/check-stock` and `/api/orders/shipping-fee` only.
- Duplicate merging, first-seen issue order, ACTIVE/stock checking; no reservations or persistence.
- Narrow JDBC product projection; quote preserves legacy inclusion of inactive/deleted/low-stock
  products but rejects missing IDs. Client prices/dimensions never determine the quote.
- Weight-only active; volumetric alternative tested. Source aliases, whitespace/accent normalization,
  half-kilo tiers, strict >100000 discount and zero fee floor retained.
- User explicitly selected **BigDecimal + HALF_UP**. Regression covers0.35 -> VAT0.04 rather than
  source JS0.03. Quantity sums exceed int32 safely. Monetary response fields are JSON numbers.
- No Flyway changes: real PostgreSQL applies V1–V4; no order/cart tables, stubs or provider calls.
- Compatibility boundaries and MODULE7 stock revalidation requirement are in contract/risks.

## Reproducible validation

Java21 `/tmp/aims-java21/jdk-21.0.12.1+1/Contents/Home`, Maven Wrapper with isolated
`MAVEN_USER_HOME=/tmp/aims-maven-home` and repository `/tmp/aims-maven-repository`.
Docker Desktop / PostgreSQL17.6-alpine Testcontainers; no H2, skipped containers or external DB.

From `src/backend` with the above environment:

```sh
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository -Dtest=CartInputTest,ShippingCalculatorTest,CartIntegrationTest test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository test
./mvnw -B -Dmaven.repo.local=/tmp/aims-maven-repository verify
```

From repository root:

```sh
node tools/capture-cart-shipping.cjs
python3 tools/verify-frontend.py
git diff --check
git -C ../ISD.20252-25 status --short
git -C ../ISD.20252-25 rev-parse HEAD
```

The offline oracle captures44 original ValidationPipe cases and472 original shipping cases using
synthetic values, without Nest startup, environment loading, database or network calls.
CartInputTest has2 tests (including44 fixtures); ShippingCalculatorTest has472 dynamic original
cases plus1 VAT boundary test; CartIntegrationTest has7 PostgreSQL/MockMvc tests.

Final results: module482/482, full suite560/560, Maven verify560/560, zero failures/errors/skips;
executable JAR built. Logs: `/tmp/aims-module6-tests.log`, `/tmp/aims-module6-suite.log`,
`/tmp/aims-module6-build.log`. A test generic-overload compilation error was fixed before final runs.
Frontend70/70 unchanged, migration SQL unchanged, whitespace review passed. Source HEAD unchanged
and original status preserved: modified `.DS_Store`, `src/.DS_Store`, untracked
`ArchitecturalDesign/ActivityDiagram/`.

Integration checks include public/stale-JWT access, CORS/preflight/no-store, validation envelopes,
trailing slashes, missing products, status/stock distinctions, duplicate overflow, decimal correction,
quote dimension exclusion and unchanged stocks/audit/schema. Other order routes remain403.

Implementation and final completion hashes are recorded in the subsequent status commit and
checkpoint response; each push must be verified against origin/main. MODULE7 is not authorized.
