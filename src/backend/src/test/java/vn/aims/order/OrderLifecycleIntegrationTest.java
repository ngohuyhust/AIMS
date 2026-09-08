package vn.aims.order;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class OrderLifecycleIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
    static final java.util.concurrent.atomic.AtomicInteger REFUNDS=new java.util.concurrent.atomic.AtomicInteger();
    static final com.sun.net.httpserver.HttpServer SERVER=server();
    static com.sun.net.httpserver.HttpServer server() {
        try {var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",e->{String body;if(e.getRequestURI().getPath().equals("/v1/oauth2/token")) body="{\"access_token\":\"test-oauth\",\"expires_in\":300}";
                else {REFUNDS.incrementAndGet();body="{\"id\":\"REFUND-1\",\"status\":\"COMPLETED\",\"amount\":{\"currency_code\":\"USD\",\"value\":\"5.28\"}}";}
                byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();return server;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    @org.springframework.test.context.DynamicPropertySource static void config(org.springframework.test.context.DynamicPropertyRegistry p) {
        p.add("PAYPAL_API_BASE_URL",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());p.add("PAYPAL_CLIENT_ID",()->"test-client");p.add("PAYPAL_CLIENT_SECRET",()->"test-secret");
    }
    @AfterAll static void stop() {SERVER.stop(0);}
    @Autowired vn.aims.payment.PaymentService payments;
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired vn.aims.auth.JwtTokens jwt;
    @BeforeEach void seed() {
        REFUNDS.set(0);jdbc.update("DELETE FROM orders");jdbc.update("DELETE FROM product_logs");jdbc.update("DELETE FROM products");
        jdbc.update("INSERT INTO products(product_id,product_type,title,category,barcode,weight,original_value,current_price,quantity_in_stock) VALUES (1,'BOOK','Test','Book','lifecycle-1',0.5,100000,100000,3)");
        for(int id=1;id<=2;id++) {
            jdbc.update("INSERT INTO orders(order_id,sub_total,tax,shipping_fee,total_payment,customer_access_token) VALUES (?,100000,10000,22000,132000,?)",id,(id==1?"a":"b").repeat(64));
            jdbc.update("INSERT INTO order_items(order_id,product_id,quantity,unit_price) VALUES (?,1,1,100000)",id);
        }
    }
    String auth() {return "Bearer "+jwt.issue(7,"manager@example.test","Manager",List.of("PRODUCT_MANAGER"));}
    JsonNode action(int id,String action,int expected) throws Exception {return json.readTree(mvc.perform(post("/api/orders/"+id+"/"+action).header("Authorization",auth())).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());}
    int paid(String method) {jdbc.update("UPDATE orders SET status='PENDING_PROCESSING' WHERE order_id=1");return jdbc.queryForObject("INSERT INTO payment_transactions(order_id,method,amount,status) VALUES (1,?,132000,'SUCCESS') RETURNING transaction_id",Integer.class,method);}
    @Test void unpaidCancelRestoresStockOnceAndRedactsManagerCapability() throws Exception {
        assertThat(action(1,"cancel",201).has("customerAccessToken")).isFalse();action(1,"cancel",201);
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products",Integer.class)).isEqualTo(4);action(1,"reject",400);
    }
    @Test void concurrentCancelAndRejectCannotRestoreTwice() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->mvc.perform(post("/api/orders/1/cancel").header("Authorization",auth())).andReturn().getResponse().getStatus());
            var b=pool.submit(()->mvc.perform(post("/api/orders/1/reject").header("Authorization",auth())).andReturn().getResponse().getStatus());
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,400);
        }
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products",Integer.class)).isEqualTo(4);
    }
    @Test void customerOnlyCancelsOwnUnpaidOrder() throws Exception {
        mvc.perform(post("/api/customer/orders/1/cancel").param("token","b".repeat(64))).andExpect(status().isNotFound());
        paid("VIETQR");mvc.perform(post("/api/customer/orders/1/cancel").param("token","a".repeat(64))).andExpect(status().isForbidden());
        mvc.perform(post("/api/customer/orders/2/cancel").param("token","b".repeat(64))).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products",Integer.class)).isEqualTo(4);
    }
    @Test void manualRefundChangesSharedStateWithoutRestoringStockAgain() throws Exception {
        int payment=paid("VIETQR");assertThat(action(1,"reject",201).path("status").asText()).isEqualTo("REFUND_PENDING");
        assertThat(action(1,"confirm-vietqr-refund",201).path("status").asText()).isEqualTo("REFUNDED");action(1,"confirm-vietqr-refund",201);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions WHERE transaction_id=?",String.class,payment)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products",Integer.class)).isEqualTo(4);
    }
    @Test void pendingPaymentBlocksApprovalAndCancellation() throws Exception {
        jdbc.update("INSERT INTO payment_transactions(order_id,method,amount,status) VALUES (1,'PAYPAL',132000,'PENDING')");
        action(1,"approve",409);action(1,"cancel",409);assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products",Integer.class)).isEqualTo(3);
    }
    @Test void sourceUnpaidApprovalAndIllegalTransitionsRemainGuarded() throws Exception {
        assertThat(action(1,"approve",201).path("status").asText()).isEqualTo("APPROVED");action(1,"cancel",400);action(1,"reject",400);action(1,"approve",400);
    }
    @Test void pendingListPaginationSearchAndRoleProtection() throws Exception {
        paid("VIETQR");
        mvc.perform(get("/api/orders/pending")).andExpect(status().isUnauthorized());
        var response=json.readTree(mvc.perform(get("/api/orders/pending").header("Authorization",auth()).param("search","#1").param("paymentMethod","VIETQR").param("limit","100"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(response.path("total").asInt()).isEqualTo(1);assertThat(response.path("limit").asInt()).isEqualTo(30);assertThat(response.path("items").get(0).has("customerAccessToken")).isFalse();
    }
    void paypalPaid() {int id=paid("PAYPAL");jdbc.update("INSERT INTO paypal_transactions(transaction_id,paypal_order_id,paypal_capture_id,status) VALUES (?,'P1','C1','COMPLETED')",id);}
    @Test void paypalCancellationConcurrentRetriesRefundAndRestoreOnlyOnce() throws Exception {
        paypalPaid();try(var pool=Executors.newFixedThreadPool(2)) {var a=pool.submit(()->action(1,"cancel",201));var b=pool.submit(()->action(1,"cancel",201));a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);}
        assertThat(REFUNDS.get()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("REFUNDED");
    }
    @Test void stockFailureAfterPaypalRefundRecoversFromDurableOperation() throws Exception {
        paypalPaid();jdbc.execute("ALTER TABLE products ADD CONSTRAINT refuse_restore CHECK(quantity_in_stock<=3)");
        try {action(1,"cancel",500);}finally{jdbc.execute("ALTER TABLE products DROP CONSTRAINT refuse_restore");}
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=1",String.class)).isEqualTo("PENDING_PROCESSING");
        action(1,"approve",409);action(1,"cancel",201);assertThat(REFUNDS.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products",Integer.class)).isEqualTo(4);
    }
    @Test void approvedOrderCannotBeRefundedThroughDirectPaypalApi() throws Exception {
        paypalPaid();action(1,"approve",201);
        mvc.perform(post("/api/paypal/order/refund").header("Authorization",auth()).contentType("application/json").content("{\"orderID\":1}")).andExpect(status().isConflict());assertThat(REFUNDS.get()).isZero();
    }
    @Test void manualRefundFailureRollsBackSharedPayment() throws Exception {
        paid("VIETQR");action(1,"cancel",201);jdbc.execute("ALTER TABLE orders ADD CONSTRAINT refuse_refunded CHECK(status <> 'REFUNDED')");
        try {action(1,"confirm-vietqr-refund",500);}finally{jdbc.execute("ALTER TABLE orders DROP CONSTRAINT refuse_refunded");}
        assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("SUCCESS");action(1,"confirm-vietqr-refund",201);
    }
    @Test void v8UpgradePreservesOrdersAndIsRepeatable() {
        var old=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas("upgrade_lifecycle").defaultSchema("upgrade_lifecycle").target("8").load();old.migrate();
        jdbc.update("INSERT INTO upgrade_lifecycle.orders(sub_total,tax,shipping_fee,total_payment) VALUES (100,10,22000,22110)");
        var next=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas("upgrade_lifecycle").defaultSchema("upgrade_lifecycle").target("9").load();
        assertThat(next.migrate().migrationsExecuted).isEqualTo(1);assertThat(next.migrate().migrationsExecuted).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM upgrade_lifecycle.orders",Integer.class)).isEqualTo(1);
    }

    @Test void durableCancellationBlocksNewPaymentBeforeStockRelease() throws Exception {
        jdbc.update("INSERT INTO order_lifecycle_operations(order_id,action) VALUES (1,'CANCEL')");
        assertThatThrownBy(()->payments.begin(1,"PAYPAL",new java.math.BigDecimal("132000"),"TEST")).isInstanceOf(vn.aims.payment.PaymentException.class);
        action(1,"cancel",201);assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transactions",Integer.class)).isZero();
    }
    @Test void listDateUnpaidAndRefundQueueFiltersAreConsistent() throws Exception {
        jdbc.update("UPDATE orders SET created_at=now()-interval '40 days' WHERE order_id=2");paid("VIETQR");
        var today=json.readTree(mvc.perform(get("/api/orders/pending").header("Authorization",auth()).param("dateRange","TODAY")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(today.path("total").asInt()).isEqualTo(1);
        var unpaid=json.readTree(mvc.perform(get("/api/orders/pending").header("Authorization",auth()).param("paymentMethod","UNPAID")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(unpaid.path("items").get(0).path("orderID").asInt()).isEqualTo(2);
        action(1,"cancel",201);var refunds=json.readTree(mvc.perform(get("/api/orders/vietqr-refunds").header("Authorization",auth()).param("paymentMethod","PAYPAL")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(refunds.path("total").asInt()).isEqualTo(1);
    }

}
