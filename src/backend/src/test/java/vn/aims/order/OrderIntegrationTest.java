package vn.aims.order;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class OrderIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired vn.aims.auth.JwtTokens jwt;
    static final String DELIVERY="{\"receiverName\":\"Customer\",\"email\":\"customer@example.test\",\"phoneNumber\":\"0912345678\",\"address\":\"Test address\",\"province\":\"Hà Nội\"}";
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM orders");jdbc.update("DELETE FROM product_logs");jdbc.update("DELETE FROM products");
        for(int id=1;id<=2;id++) jdbc.update("""
            INSERT INTO products(product_id,product_type,title,category,barcode,weight,original_value,current_price,quantity_in_stock)
            VALUES (?,'BOOK','Test','Book',?,0.5,50000,50000,3)
            """,id,"order-"+id);
    }
    String body(String items) { return "{\"cartItems\":"+items+",\"deliveryInfo\":"+DELIVERY+"}"; }
    JsonNode place(String items) throws Exception {
        return json.readTree(mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body(items)))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
            .andReturn().getResponse().getContentAsString());
    }
    @Test void placementMergesLinesReservesStockAndPersistsCompleteGraph() throws Exception {
        var order=place("[{\"productId\":1,\"quantity\":1},{\"productId\":2,\"quantity\":1},{\"productId\":1,\"quantity\":1}]");
        assertThat(order.path("status").asText()).isEqualTo("PENDING");
        assertThat(order.path("subTotal").asText()).isEqualTo("150000.00");
        assertThat(order.path("tax").asText()).isEqualTo("15000.00");
        assertThat(order.path("shippingFee").asText()).isEqualTo("0.00");
        assertThat(order.path("totalPayment").asText()).isEqualTo("165000.00");
        assertThat(order.path("customerAccessToken").asText()).matches("[0-9a-f]{64}");
        assertThat(order.path("createdAt").asText()).matches(".*\\.\\d{3}Z");
        assertThat(order.path("orderItems").size()).isEqualTo(2);
        assertThat(order.path("orderItems").get(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(order.path("orderItems").get(0).path("unitPrice").asText()).isEqualTo("50000.00");
        assertThat(order.path("orderItems").get(0).path("product").path("quantityInStock").asInt()).isEqualTo(1);
        assertThat(order.path("deliveryInfo").path("deliveryNotes").isNull()).isTrue();
        assertThat(order.path("invoice").path("totalIncludeVAT").asText()).isEqualTo("165000.00");
        assertThat(jdbc.queryForList("SELECT quantity_in_stock FROM products ORDER BY product_id",Integer.class)).containsExactly(1,2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices",Integer.class)).isEqualTo(1);
        assertThat(order.has("paymentMethod")).isFalse();
    }
    @Test void concurrentLastUnitOnlyCreatesOneOrder() throws Exception {
        jdbc.update("UPDATE products SET quantity_in_stock=1 WHERE product_id=1");
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<Future<Integer>>();
            for(int i=0;i<2;i++) futures.add(pool.submit(()->{start.await();return mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON).content(body("[{\"productId\":1,\"quantity\":1}]"))).andReturn().getResponse().getStatus();}));
            start.countDown();assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,400);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products WHERE product_id=1",Integer.class)).isZero();
    }
    @Test void invoiceFailureRollsBackStockOrderItemsAndDelivery() throws Exception {
        jdbc.execute("ALTER TABLE invoices ADD CONSTRAINT test_invoice_failure CHECK (total_payment < 1)");
        try {
            mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body("[{\"productId\":1,\"quantity\":1}]")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("Internal server error"));
            for(String table:List.of("orders","order_items","delivery_info","invoices"))
                assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products WHERE product_id=1",Integer.class)).isEqualTo(3);
        } finally { jdbc.execute("ALTER TABLE invoices DROP CONSTRAINT test_invoice_failure"); }
    }
    @Test void ownershipRequiredAndManagerCanReadButCannotEditOrObtainCapability() throws Exception {
        var order=place("[{\"productId\":1,\"quantity\":1}]");int id=order.path("orderID").asInt();String token=order.path("customerAccessToken").asText();
        mvc.perform(get("/api/orders/"+id)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.customerAccessToken").doesNotExist());
        mvc.perform(get("/api/orders/"+id).header("x-order-token","wrong")).andExpect(status().isNotFound());
        var other=place("[{\"productId\":1,\"quantity\":1}]");
        mvc.perform(get("/api/orders/"+id).header("x-order-token",other.path("customerAccessToken").asText())).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/"+id+"/").header("x-order-token",token).header("Authorization","Bearer stale"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.paymentMethod").isEmpty()).andExpect(jsonPath("$.deliveryInfo.email").value("customer@example.test"));
        mvc.perform(get("/api/customer/orders/"+id)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/customer/orders/"+id).param("token","wrong")).andExpect(status().isNotFound());
        mvc.perform(get("/api/customer/orders/"+id).param("token",token)).andExpect(status().isOk());
        String manager="Bearer "+jwt.issue(7,"manager@example.test","Manager",List.of("PRODUCT_MANAGER"));
        mvc.perform(get("/api/orders/"+id).header("Authorization",manager)).andExpect(status().isOk()).andExpect(jsonPath("$.customerAccessToken").doesNotExist());
        mvc.perform(get("/api/orders/"+id).header("Authorization","Bearer "+jwt.issue(8,"admin@example.test","Admin",List.of("ADMIN")))).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/orders/"+id+"/delivery-info").header("Authorization",manager).contentType(MediaType.APPLICATION_JSON).content(DELIVERY)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/orders/"+id+"/delivery-info").header("x-order-token","wrong").contentType(MediaType.APPLICATION_JSON).content(DELIVERY)).andExpect(status().isNotFound());
    }
    @Test void deliveryRepricesInvoicePreservesOriginalAmountsNotesAndStock() throws Exception {
        var order=place("[{\"productId\":1,\"quantity\":1}]");int id=order.path("orderID").asInt();String token=order.path("customerAccessToken").asText();
        jdbc.update("UPDATE products SET current_price=60000 WHERE product_id=1");
        jdbc.update("UPDATE delivery_info SET delivery_notes='keep' WHERE order_id=?",id);
        String changed=DELIVERY.replace("Hà Nội","Đà Nẵng");
        mvc.perform(patch("/api/orders/"+id+"/delivery-info/").header("x-order-token",token).contentType(MediaType.APPLICATION_JSON).content(changed))
            .andExpect(status().isOk()).andExpect(jsonPath("$.subTotal").value("50000.00"))
            .andExpect(jsonPath("$.totalPayment").value("85000.00")).andExpect(jsonPath("$.invoice.totalPayment").value("85000.00"))
            .andExpect(jsonPath("$.deliveryInfo.deliveryNotes").value("keep"));
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products WHERE product_id=1",Integer.class)).isEqualTo(2);
        jdbc.update("UPDATE orders SET status='APPROVED' WHERE order_id=?",id);
        mvc.perform(patch("/api/orders/"+id+"/delivery-info").header("x-order-token",token).contentType(MediaType.APPLICATION_JSON).content(DELIVERY))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Order "+id+" cannot update delivery info from status APPROVED"));
    }
    @Test void deliveryFailureRollsBackWholeUpdateAndMissingRelationsCanBeRecreated() throws Exception {
        var order=place("[{\"productId\":1,\"quantity\":1}]");int id=order.path("orderID").asInt();String token=order.path("customerAccessToken").asText();
        jdbc.execute("ALTER TABLE invoices ADD CONSTRAINT test_update_failure CHECK (shipping_fee < 30000)");
        try {
            mvc.perform(patch("/api/orders/"+id+"/delivery-info").header("x-order-token",token).contentType(MediaType.APPLICATION_JSON).content(DELIVERY.replace("Hà Nội","Đà Nẵng")))
                .andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT province FROM delivery_info WHERE order_id=?",String.class,id)).isEqualTo("Hà Nội");
            assertThat(jdbc.queryForObject("SELECT total_payment FROM orders WHERE order_id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("77000");
        } finally { jdbc.execute("ALTER TABLE invoices DROP CONSTRAINT test_update_failure"); }
        jdbc.update("DELETE FROM delivery_info WHERE order_id=?",id);jdbc.update("DELETE FROM invoices WHERE order_id=?",id);
        mvc.perform(patch("/api/orders/"+id+"/delivery-info").header("x-order-token",token).contentType(MediaType.APPLICATION_JSON).content(DELIVERY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.invoice.totalPayment").value("77000.00"));
    }
    @Test void mixedUnavailableItemsAndMissingDeliveryNeverPartiallyReserve() throws Exception {
        jdbc.update("UPDATE products SET status='DEACTIVATED' WHERE product_id=2");
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body("[{\"productId\":1,\"quantity\":1},{\"productId\":2,\"quantity\":1},{\"productId\":99,\"quantity\":1}]")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.issues.length()").value(2)).andExpect(jsonPath("$.issues[0].productId").value(2));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"cartItems\":[{\"productId\":1,\"quantity\":1}]}"))
            .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products WHERE product_id=1",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isZero();
    }
    @Test void reversedConcurrentCartsAvoidDeadlockAndGenerateDistinctTokens() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return place("[{\"productId\":1,\"quantity\":1},{\"productId\":2,\"quantity\":1}]");});
            var b=pool.submit(()->{start.await();return place("[{\"productId\":2,\"quantity\":1},{\"productId\":1,\"quantity\":1}]");});
            start.countDown();var one=a.get(15,TimeUnit.SECONDS);var two=b.get(15,TimeUnit.SECONDS);
            assertThat(one.path("customerAccessToken")).isNotEqualTo(two.path("customerAccessToken"));
            assertThat(two.path("orderItems").get(0).path("product").path("productID").asInt()).isEqualTo(2);
        }
        assertThat(jdbc.queryForList("SELECT quantity_in_stock FROM products ORDER BY product_id",Integer.class)).containsExactly(1,1);
    }
    @Test void dtoErrorsAndFutureRoutesRemainGuarded() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message[0]").value("cartItems must contain at least 1 elements"));
        for(String path:List.of("/api/orders/pending","/api/orders/vietqr-refunds","/api/payments")) mvc.perform(get(path)).andExpect(status().isForbidden());
        for(String path:List.of("/api/orders/1/cancel","/api/orders/1/approve","/api/customer/orders/1/cancel")) mvc.perform(post(path)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.paypal_transactions') IS NULL",Boolean.class)).isTrue();
        mvc.perform(options("/api/orders/1/delivery-info").header("Origin","http://localhost:4200").header("Access-Control-Request-Method","PATCH")
            .header("Access-Control-Request-Headers","x-order-token,content-type")).andExpect(status().isNoContent())
            .andExpect(header().string("Access-Control-Allow-Headers","x-order-token,content-type"));
    }
    @Test void monetaryOverflowRollsBackReservationAndApprovedVatRoundingIsShared() throws Exception {
        jdbc.update("UPDATE products SET original_value=9999999999.99,current_price=9999999999.99 WHERE product_id=1");
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body("[{\"productId\":1,\"quantity\":1}]")))
            .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products WHERE product_id=1",Integer.class)).isEqualTo(3);
        jdbc.update("UPDATE products SET original_value=0.35,current_price=0.35 WHERE product_id=1");
        var order=place("[{\"productId\":1,\"quantity\":1}]");
        assertThat(order.path("tax").asText()).isEqualTo("0.04");
        assertThat(order.path("invoice").path("totalPayment").asText()).isEqualTo("22000.39");
    }
    @Test void concurrentDeliveryUpdatesKeepInvoiceAndOrderConsistent() throws Exception {
        var order=place("[{\"productId\":1,\"quantity\":1}]");int id=order.path("orderID").asInt();String token=order.path("customerAccessToken").asText();
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<Future<Integer>>();
            for(String province:List.of("Hà Nội","Đà Nẵng")) futures.add(pool.submit(()->{start.await();return mvc.perform(patch("/api/orders/"+id+"/delivery-info")
                .header("x-order-token",token).contentType(MediaType.APPLICATION_JSON).content(DELIVERY.replace("Hà Nội",province)))
                .andReturn().getResponse().getStatus();}));
            start.countDown();for(var future:futures) assertThat(future.get(15,TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT o.total_payment=i.total_payment AND o.shipping_fee=i.shipping_fee FROM orders o JOIN invoices i USING(order_id) WHERE order_id=?",Boolean.class,id)).isTrue();
        String province=jdbc.queryForObject("SELECT province FROM delivery_info WHERE order_id=?",String.class,id);
        assertThat(jdbc.queryForObject("SELECT shipping_fee FROM orders WHERE order_id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo(province.equals("Hà Nội")?"22000":"30000");
    }
    @Test void v4UpgradePreservesProductsAndAppliesOnlyOneNewMigration() {
        var old=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword())
            .schemas("upgrade_orders").defaultSchema("upgrade_orders").target("4").load();
        old.migrate();
        jdbc.update("INSERT INTO upgrade_orders.products(product_type,title,category,barcode,weight,original_value,current_price,quantity_in_stock) VALUES ('BOOK','Preserved','Book','upgrade',1,100,100,2)");
        var current=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword())
            .schemas("upgrade_orders").defaultSchema("upgrade_orders").target("5").load();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(current.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT title FROM upgrade_orders.products",String.class)).isEqualTo("Preserved");
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM upgrade_orders.products",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upgrade_orders.orders",Integer.class)).isZero();
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void schemaMatchesOriginalMetadata() {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
            try(var statement=connection.createStatement()) {
                statement.execute("CREATE SCHEMA legacy_orders");statement.execute("SET LOCAL search_path=legacy_orders,public");
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("order/typeorm-schema.sql"));
                statement.execute("SET LOCAL search_path=public");
            }return null;
        });
        for(String table:List.of("orders","order_items","delivery_info","invoices")) {
            String columns="""
                SELECT column_name,data_type,is_nullable,character_maximum_length,numeric_precision,numeric_scale,datetime_precision,
                  replace(replace(column_default,'legacy_orders.',''),'public.','') AS default_value
                FROM information_schema.columns WHERE table_schema=? AND table_name=? ORDER BY column_name
                """;
            assertThat(jdbc.queryForList(columns,"public",table)).isEqualTo(jdbc.queryForList(columns,"legacy_orders",table));
            String constraints="""
                SELECT c.conname,c.contype,replace(replace(pg_get_constraintdef(c.oid),'legacy_orders.',''),'public.','') AS definition
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname=? ORDER BY c.conname
                """;
            assertThat(jdbc.queryForList(constraints,"public",table)).isEqualTo(jdbc.queryForList(constraints,"legacy_orders",table));
            String indexes="SELECT indexname,replace(replace(indexdef,'legacy_orders.',''),'public.','') AS definition FROM pg_indexes WHERE schemaname=? AND tablename=? ORDER BY indexname";
            assertThat(jdbc.queryForList(indexes,"public",table)).isEqualTo(jdbc.queryForList(indexes,"legacy_orders",table));
        }
    }
}
