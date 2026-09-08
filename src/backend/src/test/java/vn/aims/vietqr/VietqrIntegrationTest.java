package vn.aims.vietqr;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.aims.payment.*;
import vn.aims.payment.gateway.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers @SpringBootTest @org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class VietqrIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
    static final ObjectMapper JSON=new ObjectMapper();static final AtomicInteger GENERATES=new AtomicInteger();
    static volatile boolean upstreamError;
    static final HttpServer SERVER=start();
    static HttpServer start() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",e->{
                String response;int status=200;
                if(e.getRequestURI().getPath().endsWith("token_generate")) response="{\"access_token\":\"synthetic-token\"}";
                else {int n=GENERATES.incrementAndGet();var body=JSON.readTree(e.getRequestBody());String order=body.path("orderId").asText();
                    response="{\"qrCode\":\"000201TEST\",\"transactionRefId\":\"REF-"+n+"\",\"orderId\":\""+order+"\"}";
                    if(upstreamError) {status=500;response="{}";}
                }
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();
            });server.start();return server;
        } catch(Exception e) {throw new IllegalStateException(e);}
    }
    @DynamicPropertySource static void config(DynamicPropertyRegistry p) {
        p.add("VIETQR_API_BASE_URL",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());
        p.add("VIETQR_MERCHANT_USERNAME",()->"merchant-test");p.add("VIETQR_MERCHANT_PASSWORD",()->"merchant-test-password");
        p.add("VIETQR_USERNAME",()->"test-user");p.add("VIETQR_PASSWORD",()->"test-password");
        p.add("VIETQR_BANK_CODE",()->"VCB");p.add("VIETQR_BANK_ACCOUNT",()->"12345678");p.add("VIETQR_BANK_ACCOUNT_NAME",()->"TEST BANK");
    }
    @AfterAll static void stop() {SERVER.stop(0);}
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired vn.aims.auth.JwtTokens jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired VietqrGateway gateway;
    @Autowired PaymentService payments;
    @Autowired PlatformTransactionManager manager;
    TransactionTemplate tx;
    @BeforeEach void setup() {
        tx=new TransactionTemplate(manager);jdbc.update("DELETE FROM orders");GENERATES.set(0);upstreamError=false;
        for(int i=1;i<=2;i++) jdbc.update("INSERT INTO orders(order_id,sub_total,tax,shipping_fee,total_payment,customer_access_token) VALUES (?,100000,10000,22000,132000,?)",i,(i==1?"a":"b").repeat(64));
    }
    GatewayRequest prepare(int order) {
        return tx.execute(s->{var p=payments.begin(order,"VIETQR",new BigDecimal("132000"),"AIMS "+order);var request=new GatewayRequest(order,p.transactionID(),p.amount(),"AIMS "+order);gateway.prepare(request);return request;});
    }
    JsonNode create(int order) {var request=prepare(order);return tx.execute(s->gateway.createPayment(request));}
    JsonNode callback(int order,String reference,String bankId) {
        var node=JSON.createObjectNode();node.put("orderId",Integer.toString(order)).put("amount",132000).put("bankaccount","12345678").put("content","AIMS "+order).put("transType","C").put("transactionid",bankId).put("referencenumber",reference).put("transactiontime",Instant.now().toEpochMilli()).put("sign","DO_NOT_STORE_SIGNATURE");return node;
    }
    boolean apply(JsonNode callback) {return tx.execute(s->{var r=gateway.handleCallback(callback);if(r.changed()) payments.confirm(r.confirmation());return r.changed();});}
    @Test void generateReuseConfirmAndRepeatAreAtomic() {
        var first=create(1);assertThat(create(1)).isEqualTo(first);assertThat(GENERATES.get()).isEqualTo(1);
        var c=callback(1,first.path("transactionRef").asText(),"BANK-1");assertThat(apply(c)).isTrue();assertThat(apply(c)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=1",String.class)).isEqualTo("PENDING_PROCESSING");
        assertThat(jdbc.queryForObject("SELECT raw_callback::text FROM vietqr_transactions",String.class)).doesNotContain("sign","bankaccount");
    }
    @Test void concurrentCreateAndCallbacksHaveOneEffect() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->create(1));var b=pool.submit(()->create(1));var qr=a.get(20,TimeUnit.SECONDS);assertThat(b.get(20,TimeUnit.SECONDS)).isEqualTo(qr);
            var c=callback(1,qr.path("transactionRef").asText(),"BANK-1");var x=pool.submit(()->apply(c));var y=pool.submit(()->apply(c));assertThat(java.util.List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(GENERATES.get()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM vietqr_receipts",Integer.class)).isEqualTo(1);
    }
    @Test void wrongBankOrderFractionalAmountAndDebitCannotPay() {
        var qr=create(1);var good=(com.fasterxml.jackson.databind.node.ObjectNode)callback(1,qr.path("transactionRef").asText(),"BANK-1");
        for(String field:java.util.List.of("bankaccount","orderId","transType","content")) {var bad=good.deepCopy();bad.put(field,field.equals("orderId")?"2":"WRONG");assertThatThrownBy(()->apply(bad)).isInstanceOf(PaymentException.class);}
        var bad=good.deepCopy();bad.put("amount",new BigDecimal("132000.01"));assertThatThrownBy(()->apply(bad)).isInstanceOf(PaymentException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("PENDING");
    }
    @Test void bankReceiptCannotPayTwoOrders() {
        var a=create(1);var b=create(2);apply(callback(1,a.path("transactionRef").asText(),"BANK-1"));
        assertThatThrownBy(()->apply(callback(2,b.path("transactionRef").asText(),"BANK-1"))).isInstanceOf(PaymentException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=2",String.class)).isEqualTo("PENDING");
    }
    @Test void expirationFailsSharedPaymentAndLateCallbackCannotReviveIt() {
        var qr=create(1);int id=qr.path("paymentId").asInt();jdbc.update("UPDATE vietqr_transactions SET expired_at=now()-interval '1 second'");
        assertThatThrownBy(()->apply(callback(1,qr.path("transactionRef").asText(),"BANK-1"))).isInstanceOf(PaymentException.class);
        var response=tx.execute(s->{var r=gateway.getStatusByPaymentId(id);if(r.expired()) payments.fail(r.paymentTransactionId());return r.response();});
        assertThat(response.path("status").asText()).isEqualTo("EXPIRED");assertThat(payments.find(id).orElseThrow().status()).isEqualTo(PaymentStatus.FAILED);
        var next=create(1);assertThat(next.path("paymentId").asInt()).isNotEqualTo(id);
        assertThatThrownBy(()->apply(callback(1,"UNKNOWN-REF","BANK-1"))).isInstanceOf(PaymentException.class).hasMessageContaining("Ambiguous");
    }
    @Test void coreFailureRollsBackPaidRowAndReceipt() {
        var qr=create(1);jdbc.update("UPDATE orders SET status='CANCELLED' WHERE order_id=1");
        assertThatThrownBy(()->apply(callback(1,qr.path("transactionRef").asText(),"BANK-1"))).isInstanceOf(PaymentException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM vietqr_transactions",String.class)).isEqualTo("PENDING");assertThat(jdbc.queryForObject("SELECT count(*) FROM vietqr_receipts",Integer.class)).isZero();
    }
    @Test void generationFailureKeepsStablePendingAttemptForRetry() {
        var request=prepare(1);upstreamError=true;assertThatThrownBy(()->tx.execute(s->gateway.createPayment(request))).isInstanceOf(PaymentException.class);
        assertThat(payments.find(request.paymentTransactionId()).orElseThrow().status()).isEqualTo(PaymentStatus.PENDING);
        upstreamError=false;assertThat(create(1).path("paymentId").asInt()).isEqualTo(request.paymentTransactionId());
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void exactLegacySchemaAndNullableRelation() {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{try(var s=c.createStatement()) {s.execute("CREATE SCHEMA legacy_vietqr");s.execute("SET LOCAL search_path=legacy_vietqr,public");s.execute("CREATE TABLE payment_transactions(transaction_id integer PRIMARY KEY)");org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ClassPathResource("vietqr/typeorm-schema.sql"));s.execute("SET LOCAL search_path=public");}return null;});
        String columns="SELECT column_name,data_type,is_nullable,character_maximum_length,numeric_precision,numeric_scale,datetime_precision,replace(replace(column_default,'legacy_vietqr.',''),'public.','') AS default_value FROM information_schema.columns WHERE table_schema=? AND table_name='vietqr_transactions' ORDER BY column_name";
        assertThat(jdbc.queryForList(columns,"public")).isEqualTo(jdbc.queryForList(columns,"legacy_vietqr"));
        String constraints="SELECT c.conname,c.contype,replace(replace(pg_get_constraintdef(c.oid),'legacy_vietqr.',''),'public.','') AS definition FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname=? AND t.relname='vietqr_transactions' ORDER BY c.conname";
        assertThat(jdbc.queryForList(constraints,"public")).isEqualTo(jdbc.queryForList(constraints,"legacy_vietqr"));
    }
    @Test void v7UpgradePreservesExistingPaymentAndIsRepeatable() {
        var old=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas("upgrade_vietqr").defaultSchema("upgrade_vietqr").target("7").load();old.migrate();
        jdbc.update("INSERT INTO upgrade_vietqr.payment_transactions(method,amount) VALUES ('PAYPAL',132000)");
        var next=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas("upgrade_vietqr").defaultSchema("upgrade_vietqr").target("8").load();
        assertThat(next.migrate().migrationsExecuted).isEqualTo(1);assertThat(next.migrate().migrationsExecuted).isZero();assertThat(jdbc.queryForObject("SELECT amount FROM upgrade_vietqr.payment_transactions",BigDecimal.class)).isEqualByComparingTo("132000");
    }
    String merchantToken() throws Exception {
        var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/vqr/api/token_generate")
            .header("Authorization","Basic "+java.util.Base64.getEncoder().encodeToString("merchant-test:merchant-test-password".getBytes(StandardCharsets.UTF_8))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"))
            .andReturn().getResponse().getContentAsString();
        return JSON.readTree(result).path("access_token").asText();
    }
    JsonNode httpCreate(int order,String token,int expected) throws Exception {
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/vietqr/payments")
            .header("x-order-token",token).contentType("application/json").content("{\"orderId\":"+order+",\"amount\":132000,\"content\":\"AIMS "+order+"\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(expected)).andReturn().getResponse().getContentAsString();return JSON.readTree(response);
    }
    @Test void protectedHttpCreateStatusAndMerchantCallbackKeepResponseContract() throws Exception {
        var qr=httpCreate(1,"a".repeat(64),201);int id=qr.path("paymentId").asInt();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/vietqr/payments/"+id+"/status").header("x-order-token","a".repeat(64)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paymentContent").value("AIMS 1"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/vietqr/payments/by-ref/"+qr.path("transactionRef").asText()+"/status").header("x-order-token","a".repeat(64)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var callback=callback(1,qr.path("transactionRef").asText(),"BANK-HTTP");var token=merchantToken();
        for(String path:java.util.List.of("/api/vietqr/payments/callback","/vqr/bank/api/transaction-callback","/vqr/bank/api/transaction-sync/"))
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization","Bearer "+token).contentType("application/json").content(callback.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("SUCCESS"));
        assertThat(payments.find(id).orElseThrow().status()).isEqualTo(PaymentStatus.SUCCESS);
    }
    @Test void ownershipAndMerchantAuthenticationRejectBeforeSideEffects() throws Exception {
        httpCreate(1,"",401);httpCreate(2,"a".repeat(64),404);assertThat(GENERATES.get()).isZero();
        var qr=httpCreate(1,"a".repeat(64),201);int id=qr.path("paymentId").asInt();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/vietqr/payments/"+id+"/status").header("x-order-token","b".repeat(64)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        for(String token:java.util.List.of("bad-token",jwt.issue(7,"manager@example.test","Manager",java.util.List.of("PRODUCT_MANAGER"))))
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/vqr/bank/api/transaction-sync").header("Authorization","Bearer "+token).contentType("application/json").content(callback(1,qr.path("transactionRef").asText(),"BANK-HTTP").toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/vqr/api/token_generate").header("Authorization","Basic invalid"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        assertThat(payments.find(id).orElseThrow().status()).isEqualTo(PaymentStatus.PENDING);
    }
    @Test void testTriggerIsDisabledEvenForManagerAndStatusExpiresAtomically() throws Exception {
        var qr=httpCreate(1,"a".repeat(64),201);int id=qr.path("paymentId").asInt();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/vietqr/payments/"+id+"/trigger-callback")
            .header("Authorization","Bearer "+jwt.issue(7,"manager@example.test","Manager",java.util.List.of("PRODUCT_MANAGER"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        jdbc.update("UPDATE vietqr_transactions SET expired_at=now()-interval '1 second'");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/vietqr/payments/"+id+"/status").header("x-order-token","a".repeat(64)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("EXPIRED"));
        assertThat(payments.find(id).orElseThrow().status()).isEqualTo(PaymentStatus.FAILED);
    }

}
