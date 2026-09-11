package vn.aims.paypal;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import vn.aims.paypal.client.PaypalApiClient;
import vn.aims.payment.exception.PaymentException;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class PaypalIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
    static final ObjectMapper JSON=new ObjectMapper();
    record Call(String path,String authorization,String requestId,String body) {}
    static final Queue<Call> CALLS=new ConcurrentLinkedQueue<>();
    static volatile String mode="normal";
    static final HttpServer SERVER=server();
    static HttpServer server() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String path=exchange.getRequestURI().getPath();String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                CALLS.add(new Call(path,exchange.getRequestHeaders().getFirst("Authorization"),exchange.getRequestHeaders().getFirst("PayPal-Request-Id"),body));
                String response;int status=200;
                if(path.equals("/v1/oauth2/token")) response="{\"access_token\":\"synthetic-oauth-only\",\"expires_in\":300}";
                else if(mode.equals("unauthorized")) {status=401;response="{}";}
                else if(mode.equals("upstream-error")) {status=500;response="{\"message\":\"DO_NOT_EXPOSE_UPSTREAM_DATA\"}";}
                else if(path.equals("/v2/checkout/orders")) response=order("CREATED");
                else if(path.endsWith("/refund") || path.startsWith("/v2/payments/refunds/")) response="{\"id\":\"REFUND-1\",\"status\":\""+(mode.equals("pending-refund")?"PENDING":"COMPLETED")+"\",\"amount\":{\"currency_code\":\"USD\",\"value\":\"5.28\"}}";
                else response=order(mode.equals("pending-capture")?"APPROVED":"COMPLETED");
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();return server;
        } catch(Exception error) { throw new IllegalStateException(error); }
    }
    static String order(String status) {
        String amount=mode.equals("wrong-amount")?"6.28":"5.28";
        return "{\"id\":\"PAYPAL-1\",\"intent\":\"CAPTURE\",\"status\":\""+status+"\",\"links\":[{\"rel\":\"approve\",\"href\":\"https://www.sandbox.paypal.com/checkoutnow?token=PAYPAL-1\"}],\"purchase_units\":[{\"reference_id\":\"1\",\"amount\":{\"currency_code\":\"USD\",\"value\":\""+amount+"\"}"+
            (status.equals("COMPLETED")?",\"payments\":{\"captures\":[{\"id\":\"CAPTURE-1\",\"status\":\"COMPLETED\",\"amount\":{\"currency_code\":\"USD\",\"value\":\""+amount+"\"}}]}":"")+"}]}";
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("PAYPAL_API_BASE_URL",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());
        p.add("PAYPAL_CLIENT_ID",()->"synthetic-client");p.add("PAYPAL_CLIENT_SECRET",()->"synthetic-secret");
        p.add("APP_PUBLIC_URL",()->"http://localhost:4200/");
    }
    @AfterAll static void stop() { SERVER.stop(0); }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired vn.aims.auth.security.JwtTokens jwt;
    @BeforeEach void setup() {
        mode="normal";CALLS.clear();jdbc.update("DELETE FROM orders");
        for(int id=1;id<=2;id++) jdbc.update("INSERT INTO orders(order_id,sub_total,tax,shipping_fee,total_payment,customer_access_token) VALUES (?,100000,10000,22000,132000,?)",id,(id==1?"a":"b").repeat(64));
    }
    JsonNode request(String action,String body,int expected) throws Exception {
        var builder=post("/api/paypal/order/"+action).header("x-order-token","a".repeat(64)).contentType("application/json").content(body);
        if(action.equals("refund")) builder.header("Authorization","Bearer "+jwt.issue(7,"manager@example.test","Manager",List.of("PRODUCT_MANAGER")));
        return JSON.readTree(mvc.perform(builder).andExpect(status().is(expected)).andExpect(header().string("Cache-Control","no-store"))
            .andReturn().getResponse().getContentAsString());
    }
    JsonNode create() throws Exception { return request("create","{\"orderID\":1}",201); }
    JsonNode capture(int expected) throws Exception { return request("capture","{\"orderID\":1,\"paypalOrderID\":\"PAYPAL-1\"}",expected); }
    @Test void oauthCreateCaptureAndRefundKeepContractsAndConfirmSharedTransaction() throws Exception {
        assertThat(create().path("paypalOrderID").asText()).isEqualTo("PAYPAL-1");
        var createCall=CALLS.stream().filter(c->c.path().equals("/v2/checkout/orders")).findFirst().orElseThrow();
        var payload=JSON.readTree(createCall.body());
        assertThat(payload.path("purchase_units").get(0).path("amount").path("value").asText()).isEqualTo("5.28");
        assertThat(payload.path("application_context").path("return_url").asText()).isEqualTo("http://localhost:4200/payment?orderId=1&success=true");
        assertThat(createCall.requestId()).isNotBlank();
        assertThat(capture(201).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=1",String.class)).isEqualTo("PENDING_PROCESSING");
        assertThat(request("refund","{\"orderID\":1}",201).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("REFUNDED");
    }
    @Test void ownershipAndRefundRoleAreRequiredBeforeAnyNetworkRequest() throws Exception {
        mvc.perform(post("/api/paypal/order/create").contentType("application/json").content("{\"orderID\":1}")).andExpect(status().isUnauthorized());
        request("create","{\"orderID\":2}",404);
        mvc.perform(post("/api/paypal/order/refund").header("x-order-token","a".repeat(64)).contentType("application/json").content("{\"orderID\":1}")).andExpect(status().isUnauthorized());
        assertThat(CALLS).isEmpty();
    }
    @Test void duplicateRequestsReuseProviderResultsWithoutDuplicateMoneyCalls() throws Exception {
        create();create();capture(201);capture(201);request("refund","{\"orderID\":1}",201);request("refund","{\"orderID\":1}",201);
        for(String path:List.of("/v2/checkout/orders","/v2/checkout/orders/PAYPAL-1/capture","/v2/payments/captures/CAPTURE-1/refund"))
            assertThat(CALLS.stream().filter(c->c.path().equals(path)).count()).isEqualTo(1);
    }
    @Test void pendingCaptureAndRefundAreVerifiedByGetWithoutAnotherMoneyPost() throws Exception {
        create(); mode="pending-capture";capture(409);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("PENDING");
        mode="normal";capture(201);mode="pending-refund";request("refund","{\"orderID\":1}",409);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("SUCCESS");
        mode="normal";request("refund","{\"orderID\":1}",201);
        assertThat(CALLS.stream().filter(c->c.path().endsWith("/capture")).count()).isEqualTo(1);
        assertThat(CALLS.stream().filter(c->c.path().endsWith("/refund")).count()).isEqualTo(1);
        assertThat(CALLS.stream().anyMatch(c->c.path().equals("/v2/payments/refunds/REFUND-1"))).isTrue();
    }
    @Test void upstreamFailureRetainsPendingAndRetryUsesSameRequestIdWithoutLeakingBody() throws Exception {
        mode="upstream-error";
        assertThat(request("create","{\"orderID\":1}",502).toString()).doesNotContain("DO_NOT_EXPOSE");
        var first=CALLS.stream().filter(c->c.path().equals("/v2/checkout/orders")).findFirst().orElseThrow();
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("PENDING");
        mode="normal";create();
        assertThat(CALLS.stream().filter(c->c.path().equals(first.path())).map(Call::requestId).distinct().count()).isEqualTo(1);
    }
    @Test void expiredUncertainOperationRequiresReconciliationWithoutProviderCall() throws Exception {
        mode="upstream-error";request("create","{\"orderID\":1}",502);
        jdbc.update("UPDATE paypal_operations SET created_at=clock_timestamp()-interval '6 hours'");
        CALLS.clear();mode="normal";request("create","{\"orderID\":1}",409);assertThat(CALLS).isEmpty();
    }
    @Test void wrongGatewayOrderAndAmountNeverConfirmPayment() throws Exception {
        create();CALLS.clear();request("capture","{\"orderID\":1,\"paypalOrderID\":\"OTHER\"}",400);assertThat(CALLS).isEmpty();
        mode="wrong-amount";capture(502);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=1",String.class)).isEqualTo("PENDING");
    }
    @Test void journalRecoversAfterLocalApplyFailureWithoutRecapturing() throws Exception {
        create();
        jdbc.execute("ALTER TABLE paypal_transactions ADD CONSTRAINT reject_capture CHECK(status <> 'COMPLETED')");
        try { capture(500); } finally {jdbc.execute("ALTER TABLE paypal_transactions DROP CONSTRAINT reject_capture");}
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT response IS NOT NULL FROM paypal_operations WHERE operation='CAPTURE'",Boolean.class)).isTrue();
        capture(201);
        assertThat(CALLS.stream().filter(c->c.path().endsWith("/capture")).count()).isEqualTo(1);
    }
    @Test void concurrentCaptureAndRefundAreIdempotentAndTerminal() throws Exception {
        create();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->capture(201));var b=pool.submit(()->capture(201));a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
            var c=pool.submit(()->request("refund","{\"orderID\":1}",201));var d=pool.submit(()->request("refund","{\"orderID\":1}",201));c.get(20,TimeUnit.SECONDS);d.get(20,TimeUnit.SECONDS);
        }
        capture(409);
        assertThat(jdbc.queryForObject("SELECT status FROM paypal_transactions",String.class)).isEqualTo("REFUNDED");
        assertThat(CALLS.stream().filter(c->c.path().endsWith("/capture")).count()).isEqualTo(1);
        assertThat(CALLS.stream().filter(c->c.path().endsWith("/refund")).count()).isEqualTo(1);
    }
    @Test void invalidInputAndNonManagerCannotReachGateway() throws Exception {
        request("create","{}",400);request("capture","{\"orderID\":\"1\"}",400);
        for(String role:List.of("ADMIN","CUSTOMER")) mvc.perform(post("/api/paypal/order/refund")
            .header("Authorization","Bearer "+jwt.issue(7,"test@example.test","Test",List.of(role)))
            .contentType("application/json").content("{\"orderID\":1}")).andExpect(status().isForbidden());
        assertThat(CALLS).isEmpty();
    }
    @Test void v6UpgradePreservesDataAndIsRepeatable() {
        var old=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas("upgrade_paypal").defaultSchema("upgrade_paypal").target("6").load();old.migrate();
        jdbc.update("INSERT INTO upgrade_paypal.payment_transactions(method,amount) VALUES ('PAYPAL',132000)");
        var next=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas("upgrade_paypal").defaultSchema("upgrade_paypal").target("7").load();
        assertThat(next.migrate().migrationsExecuted).isEqualTo(1);assertThat(next.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT amount FROM upgrade_paypal.payment_transactions",java.math.BigDecimal.class)).isEqualByComparingTo("132000");
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void legacySchemaMatchesAndJournalEnforcesOneOperationPerTransaction() {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
            try(var statement=connection.createStatement()) {
                statement.execute("CREATE SCHEMA legacy_paypal");statement.execute("SET LOCAL search_path=legacy_paypal,public");
                statement.execute("CREATE TABLE payment_transactions(transaction_id integer PRIMARY KEY)");
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("paypal/typeorm-schema.sql"));
                statement.execute("SET LOCAL search_path=public");
            }return null;
        });
        String columns="SELECT column_name,data_type,is_nullable,character_maximum_length,numeric_precision,numeric_scale,replace(replace(column_default,'legacy_paypal.',''),'public.','') AS default_value FROM information_schema.columns WHERE table_schema=? AND table_name='paypal_transactions' ORDER BY column_name";
        assertThat(jdbc.queryForList(columns,"public")).isEqualTo(jdbc.queryForList(columns,"legacy_paypal"));
        String constraints="SELECT c.conname,c.contype,replace(replace(pg_get_constraintdef(c.oid),'legacy_paypal.',''),'public.','') AS definition FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname=? AND t.relname='paypal_transactions' ORDER BY c.conname";
        assertThat(jdbc.queryForList(constraints,"public")).isEqualTo(jdbc.queryForList(constraints,"legacy_paypal"));
        jdbc.update("INSERT INTO paypal_transactions(status) VALUES ('LEGACY')");
        assertThat(jdbc.queryForObject("SELECT transaction_id IS NULL FROM paypal_transactions",Boolean.class)).isTrue();
    }

    @Test void oauthUsesBasicFormCachesTokenAndInvalidatesOn401() {
        var client=new PaypalApiClient("http://127.0.0.1:"+SERVER.getAddress().getPort(),"synthetic-client","synthetic-secret","http://localhost:4200");
        var key=UUID.randomUUID();
        client.call(org.springframework.http.HttpMethod.POST,"/v2/checkout/orders",key,null);
        client.call(org.springframework.http.HttpMethod.POST,"/v2/checkout/orders",key,null);
        var oauth=CALLS.stream().filter(c->c.path().equals("/v1/oauth2/token")).toList();
        assertThat(oauth).hasSize(1);
        assertThat(oauth.getFirst().authorization()).isEqualTo("Basic "+Base64.getEncoder().encodeToString("synthetic-client:synthetic-secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(oauth.getFirst().body()).isEqualTo("grant_type=client_credentials");
        assertThat(CALLS.stream().filter(c->!c.path().equals("/v1/oauth2/token")).allMatch(c->c.authorization().equals("Bearer synthetic-oauth-only"))).isTrue();
        mode="unauthorized";
        assertThatThrownBy(()->client.call(org.springframework.http.HttpMethod.POST,"/v2/checkout/orders",key,null)).isInstanceOf(PaymentException.class);
        mode="normal";client.call(org.springframework.http.HttpMethod.POST,"/v2/checkout/orders",key,null);
        assertThat(CALLS.stream().filter(c->c.path().equals("/v1/oauth2/token")).count()).isEqualTo(2);
    }
    @Test void concurrentCreateUsesOneTransactionAndRefundApplyFailureCanRecover() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(this::create);var b=pool.submit(this::create);a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transactions",Integer.class)).isEqualTo(1);
        assertThat(CALLS.stream().filter(c->c.path().equals("/v2/checkout/orders")).count()).isEqualTo(1);
        capture(201);jdbc.execute("ALTER TABLE paypal_transactions ADD CONSTRAINT reject_refund CHECK(status <> 'REFUNDED')");
        try {request("refund","{\"orderID\":1}",500);} finally {jdbc.execute("ALTER TABLE paypal_transactions DROP CONSTRAINT reject_refund");}
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("SUCCESS");
        request("refund","{\"orderID\":1}",201);
        assertThat(CALLS.stream().filter(c->c.path().endsWith("/refund")).count()).isEqualTo(1);
    }

}
