package vn.aims.payment;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Import(PaymentIntegrationTest.EventsConfiguration.class)
class PaymentIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentService payments;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Events events;
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @TestConfiguration static class EventsConfiguration { @Bean Events events() { return new Events(); } }
    static class Events {
        final Queue<PaymentConfirmed> received=new ConcurrentLinkedQueue<>();
        @EventListener public void on(PaymentConfirmed event) { received.add(event); }
    }
    @BeforeEach void setup() {
        events.received.clear();jdbc.update("DELETE FROM orders");
        for(int id=1;id<=2;id++) jdbc.update("INSERT INTO orders(order_id,sub_total,tax,shipping_fee,total_payment,customer_access_token) VALUES (?,100000,10000,22000,132000,?)",id,(id==1?"a":"b").repeat(64));
    }
    PaymentView begin() { return payments.begin(1,"PAYPAL",new BigDecimal("132000"),"Synthetic payment"); }
    PaymentConfirmation confirmation(int transactionId) { return new PaymentConfirmation(transactionId,1,"PAYPAL",new BigDecimal("132000")); }
    String transactionStatus(int transactionId) { return jdbc.queryForObject("SELECT status FROM payment_transactions WHERE transaction_id=?",String.class,transactionId); }
    @Test void beginChecksOrderAmountAndReusesPendingAttemptWithoutProviderCalls() {
        var attempt=begin();var retry=begin();
        assertThat(retry.transactionID()).isEqualTo(attempt.transactionID());
        assertThat(attempt.amount()).isEqualByComparingTo("132000");assertThat(attempt.status()).isEqualTo(PaymentStatus.PENDING);
        assertThatThrownBy(()->payments.begin(1,"VIETQR",new BigDecimal("1"),"wrong")).isInstanceOf(PaymentException.class);
        assertThatThrownBy(()->payments.begin(99,"PAYPAL",new BigDecimal("132000"),null)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(()->payments.begin(1,"VIETQR",new BigDecimal("132000"),null)).isInstanceOf(PaymentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transactions",Integer.class)).isEqualTo(1);
        assertThat(events.received).isEmpty();
    }
    @Test void confirmationIsAtomicAndEmitsOnlyAfterCommit() {
        var attempt=begin();
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            assertThat(payments.confirm(confirmation(attempt.transactionID()))).isTrue();
            assertThat(events.received).isEmpty();
        });
        assertThat(transactionStatus(attempt.transactionID())).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=1",String.class)).isEqualTo("PENDING_PROCESSING");
        assertThat(events.received).containsExactly(new PaymentConfirmed(1,attempt.transactionID()));
        jdbc.update("UPDATE orders SET status='APPROVED' WHERE order_id=1");
        assertThat(payments.confirm(confirmation(attempt.transactionID()))).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=1",String.class)).isEqualTo("APPROVED");
        assertThat(events.received).hasSize(1);
    }
    @Test void rollbackCannotEmitEventOrLeaveSuccessfulTransaction() {
        var attempt=begin();
        new TransactionTemplate(transactions).executeWithoutResult(tx->{payments.confirm(confirmation(attempt.transactionID()));tx.setRollbackOnly();});
        assertThat(transactionStatus(attempt.transactionID())).isEqualTo("PENDING");assertThat(events.received).isEmpty();
        jdbc.execute("ALTER TABLE orders ADD CONSTRAINT test_payment_failure CHECK (status <> 'PENDING_PROCESSING')");
        try { assertThatThrownBy(()->payments.confirm(confirmation(attempt.transactionID()))).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE orders DROP CONSTRAINT test_payment_failure"); }
        assertThat(transactionStatus(attempt.transactionID())).isEqualTo("PENDING");assertThat(events.received).isEmpty();
    }
    @Test void wrongOrderMethodAmountAndTerminalOrderCannotConfirm() {
        var attempt=begin();int id=attempt.transactionID();
        for(var proof:List.of(new PaymentConfirmation(id,2,"PAYPAL",new BigDecimal("132000")),
                new PaymentConfirmation(id,1,"VIETQR",new BigDecimal("132000")),new PaymentConfirmation(id,1,"PAYPAL",new BigDecimal("132001"))))
            assertThatThrownBy(()->payments.confirm(proof)).isInstanceOf(PaymentException.class);
        jdbc.update("UPDATE orders SET status='CANCELLED' WHERE order_id=1");
        assertThatThrownBy(()->payments.confirm(confirmation(id))).isInstanceOf(PaymentException.class);
        assertThat(transactionStatus(id)).isEqualTo("PENDING");assertThat(events.received).isEmpty();
    }
    @Test void concurrentDuplicateConfirmationsPublishOnce() throws Exception {
        var attempt=begin();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->{start.await();return payments.confirm(confirmation(attempt.transactionID()));});
            var two=pool.submit(()->{start.await();return payments.confirm(confirmation(attempt.transactionID()));});
            start.countDown();assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(events.received).hasSize(1);
    }
    @Test void failureAndRefundAreConditionalAndCannotReviveOrOverwriteTerminalStates() {
        var failed=begin();assertThat(payments.fail(failed.transactionID())).isTrue();assertThat(payments.fail(failed.transactionID())).isFalse();
        assertThatThrownBy(()->payments.confirm(confirmation(failed.transactionID()))).isInstanceOf(PaymentException.class);
        var paid=begin();payments.confirm(confirmation(paid.transactionID()));
        assertThat(payments.fail(paid.transactionID())).isFalse();
        assertThat(payments.markRefunded(confirmation(paid.transactionID()))).isTrue();
        assertThat(payments.markRefunded(confirmation(paid.transactionID()))).isFalse();
        assertThat(payments.confirm(confirmation(paid.transactionID()))).isFalse();
        assertThat(transactionStatus(paid.transactionID())).isEqualTo("REFUNDED");assertThat(events.received).hasSize(1);
    }
    @Test void concurrentBeginReturnsOneStableAttempt() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->{start.await();return begin();});var two=pool.submit(()->{start.await();return begin();});
            start.countDown();assertThat(one.get(15,TimeUnit.SECONDS).transactionID()).isEqualTo(two.get(15,TimeUnit.SECONDS).transactionID());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transactions",Integer.class)).isEqualTo(1);
    }
    @Test void expirationAndConfirmationRaceHasOneTerminalOutcome() throws Exception {
        var attempt=begin();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var confirm=pool.submit(()->{start.await();try {return payments.confirm(confirmation(attempt.transactionID()));} catch(PaymentException e) {return false;}});
            var fail=pool.submit(()->{start.await();return payments.fail(attempt.transactionID());});
            start.countDown();assertThat(List.of(confirm.get(15,TimeUnit.SECONDS),fail.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        boolean paid=transactionStatus(attempt.transactionID()).equals("SUCCESS");
        assertThat(events.received).hasSize(paid?1:0);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=1",String.class)).isEqualTo(paid?"PENDING_PROCESSING":"PENDING");
    }
    static final String DELIVERY="{\"receiverName\":\"Test\",\"email\":\"test@example.test\",\"phoneNumber\":\"0912345678\",\"address\":\"Test\",\"province\":\"Đà Nẵng\"}";
    int editDelivery() throws Exception {
        return mvc.perform(patch("/api/orders/1/delivery-info").header("x-order-token","a".repeat(64))
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(DELIVERY)).andReturn().getResponse().getStatus();
    }
    @Test void activePaymentsFreezeDeliveryAndRealPaymentMethodAppearsInProtectedDetail() throws Exception {
        var attempt=begin();assertThat(editDelivery()).isEqualTo(409);
        payments.confirm(confirmation(attempt.transactionID()));assertThat(editDelivery()).isEqualTo(400);
        mvc.perform(get("/api/orders/1").header("x-order-token","a".repeat(64))).andExpect(status().isOk()).andExpect(jsonPath("$.paymentMethod").value("PAYPAL"));
        assertThat(jdbc.queryForObject("SELECT total_payment FROM orders WHERE order_id=1",BigDecimal.class)).isEqualByComparingTo("132000");
    }
    @Test void failedPaymentAllowsDeliveryEditsAndNewAmountMustMatch() throws Exception {
        var attempt=begin();payments.fail(attempt.transactionID());assertThat(editDelivery()).isEqualTo(200);
        assertThatThrownBy(this::begin).isInstanceOf(PaymentException.class);
        var next=payments.begin(1,"VIETQR",new BigDecimal("140000"),null);
        assertThat(next.amount()).isEqualByComparingTo("140000");
        assertThat(payments.latest(1).orElseThrow().transactionID()).isEqualTo(next.transactionID());
    }
    @Test void beginAndDeliveryCannotBothAcceptTheOldAmount() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var begin=pool.submit(()->{start.await();try {begin();return 201;} catch(PaymentException e) {return e.status();}});
            var edit=pool.submit(()->{start.await();return editDelivery();});
            start.countDown();var results=List.of(begin.get(15,TimeUnit.SECONDS),edit.get(15,TimeUnit.SECONDS));
            assertThat(results.equals(List.of(201,409)) || results.equals(List.of(400,200))).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transactions p JOIN orders o USING(order_id) WHERE p.amount<>round(o.total_payment)",Integer.class)).isZero();
    }
    @Test void vndRoundingAndInternalJsonPreserveMoneyAndIds() throws Exception {
        jdbc.update("UPDATE orders SET total_payment=132000.50 WHERE order_id=1");
        var payment=payments.begin(1,"PAYPAL",new BigDecimal("132001"),null);
        var tree=json.readTree(json.writeValueAsString(payment));
        assertThat(tree.path("transactionID").asInt()).isEqualTo(payment.transactionID());
        assertThat(tree.path("amount").asText()).isEqualTo("132001.00");
        assertThat(tree.path("createdAt").asText()).matches(".*\\.\\d{3}Z");
        assertThat(tree.has("customerAccessToken")).isFalse();
        assertThat(payments.find(payment.transactionID()).orElseThrow().transactionContent()).isNull();
        payments.confirm(new PaymentConfirmation(payment.transactionID(),1,"PAYPAL",new BigDecimal("132001")));
    }
    @Test void unimplementedPaymentEndpointsRemainClosed() throws Exception {
        for(String path:List.of("/api/payments","/api/orders/1/cancel"))
            mvc.perform(post(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('vietqr_transactions')",Integer.class)).isEqualTo(1);
    }
    @Test void v5UpgradePreservesOrderAndCreatesOnlySharedPaymentTable() {
        var old=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas("upgrade_payment").defaultSchema("upgrade_payment").target("5").load();
        old.migrate();
        jdbc.update("INSERT INTO upgrade_payment.orders(sub_total,tax,shipping_fee,total_payment) VALUES (100,10,22000,22110)");
        var current=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas("upgrade_payment").defaultSchema("upgrade_payment").target("6").load();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);assertThat(current.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT total_payment FROM upgrade_payment.orders",BigDecimal.class)).isEqualByComparingTo("22110");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upgrade_payment.payment_transactions",Integer.class)).isZero();
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void schemaMatchesSourceAndRetainsNullableOrderAndCascade() {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
            try(var statement=connection.createStatement()) {
                statement.execute("CREATE SCHEMA legacy_payment");statement.execute("SET LOCAL search_path=legacy_payment,public");
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("payment/typeorm-schema.sql"));
                statement.execute("SET LOCAL search_path=public");
            }return null;
        });
        String columns="""
            SELECT column_name,data_type,is_nullable,character_maximum_length,numeric_precision,numeric_scale,datetime_precision,
              replace(replace(column_default,'legacy_payment.',''),'public.','') AS default_value
            FROM information_schema.columns WHERE table_schema=? AND table_name='payment_transactions' ORDER BY column_name
            """;
        assertThat(jdbc.queryForList(columns,"public")).isEqualTo(jdbc.queryForList(columns,"legacy_payment"));
        String constraints="""
            SELECT c.conname,c.contype,replace(replace(pg_get_constraintdef(c.oid),'legacy_payment.',''),'public.','') AS definition
            FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
            WHERE n.nspname=? AND t.relname='payment_transactions' ORDER BY c.conname
            """;
        assertThat(jdbc.queryForList(constraints,"public")).isEqualTo(jdbc.queryForList(constraints,"legacy_payment"));
        String indexes="SELECT indexname,replace(replace(indexdef,'legacy_payment.',''),'public.','') AS definition FROM pg_indexes WHERE schemaname=? AND tablename='payment_transactions' ORDER BY indexname";
        assertThat(jdbc.queryForList(indexes,"public")).isEqualTo(jdbc.queryForList(indexes,"legacy_payment"));
        jdbc.update("INSERT INTO payment_transactions(method,amount) VALUES ('LEGACY',1)");
        var attempt=begin();jdbc.update("DELETE FROM orders WHERE order_id=1");
        assertThat(payments.find(attempt.transactionID())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transactions WHERE order_id IS NULL",Integer.class)).isEqualTo(1);
    }
}
