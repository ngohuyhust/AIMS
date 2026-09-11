package vn.aims.notification;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.aims.notification.event.*;
import vn.aims.notification.provider.*;
import vn.aims.notification.service.*;
import vn.aims.notification.provider.*;
import vn.aims.notification.repository.*;
import vn.aims.notification.service.*;
import vn.aims.payment.dto.*;
import vn.aims.payment.event.*;
import vn.aims.payment.exception.*;
import vn.aims.payment.service.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers @SpringBootTest
class NotificationIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
    @MockitoBean SendGridEmailProvider provider;
    @Autowired JdbcTemplate jdbc;@Autowired PaymentService payments;@Autowired NotificationDispatcher dispatcher;@Autowired PlatformTransactionManager manager;
    @BeforeEach void seed() {
        when(provider.channel()).thenReturn("EMAIL");when(provider.available()).thenReturn(true);
        jdbc.update("DELETE FROM orders");jdbc.update("INSERT INTO orders(order_id,sub_total,tax,shipping_fee,total_payment,customer_access_token) VALUES (1,100000,10000,22000,132000,?)","a".repeat(64));
        jdbc.update("INSERT INTO delivery_info(order_id,receiver_name,email,phone_number,address,province) VALUES (1,'Customer','customer@example.test','0912345678','Test address','Hà Nội')");
    }
    PaymentConfirmation begin() {var p=payments.begin(1,"VIETQR",new BigDecimal("132000"),"AIMS 1");return new PaymentConfirmation(p.transactionID(),1,"VIETQR",p.amount());}
    @Test void rollbackLeavesNoOutboxAndCommitSnapshotsExactlyOnce() throws Exception {
        var proof=begin();new TransactionTemplate(manager).executeWithoutResult(s->{payments.confirm(proof);assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox",Integer.class)).isEqualTo(1);s.setRollbackOnly();});
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox",Integer.class)).isZero();verify(provider,never()).send(any());
        payments.confirm(proof);payments.confirm(proof);assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox",Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE delivery_info SET receiver_name='Changed later' WHERE order_id=1");
        assertThat(jdbc.queryForObject("SELECT payload::text FROM notification_outbox",String.class)).doesNotContain("Changed later");
        assertThat(dispatcher.dispatchOne()).isTrue();assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox",String.class)).isEqualTo("SENT");verify(provider,times(1)).send(any());
    }
    @Test void retryableFailureDoesNotRollbackPaymentAndCanRetry() throws Exception {
        payments.confirm(begin());doThrow(new NotificationProvider.DeliveryFailure("HTTP_503",true)).doNothing().when(provider).send(any());
        dispatcher.dispatchOne();assertThat(jdbc.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("PENDING_PROCESSING");
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox",String.class)).isEqualTo("PENDING");
        jdbc.update("UPDATE notification_outbox SET available_at=now()");dispatcher.dispatchOne();assertThat(jdbc.queryForObject("SELECT attempts FROM notification_outbox",Integer.class)).isEqualTo(2);
    }
    @Test void concurrentWorkersDoNotSendSameRowTwice() throws Exception {
        payments.confirm(begin());try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(dispatcher::dispatchOne);var b=pool.submit(dispatcher::dispatchOne);a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);}verify(provider,times(1)).send(any());
    }
    @Test void disabledProviderRetainsPendingAndPermanentFailureStopsRetry() throws Exception {
        payments.confirm(begin());when(provider.available()).thenReturn(false);assertThat(dispatcher.dispatchOne()).isFalse();verify(provider,never()).send(any());
        when(provider.available()).thenReturn(true);doThrow(new NotificationProvider.DeliveryFailure("HTTP_400",false)).when(provider).send(any());dispatcher.dispatchOne();
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox",String.class)).isEqualTo("FAILED");
    }
    @Autowired NotificationOutbox outbox;
    @Test void missingRecipientSkipsWithoutProviderCall() throws Exception {
        jdbc.update("DELETE FROM delivery_info");payments.confirm(begin());dispatcher.dispatchOne();
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox",String.class)).isEqualTo("SKIPPED");verify(provider,never()).send(any());
    }
    @Test void retryLimitAndSanitizedErrors() throws Exception {
        payments.confirm(begin());doThrow(new IllegalStateException("secret recipient and token")).when(provider).send(any());
        for(int i=0;i<6;i++){jdbc.update("UPDATE notification_outbox SET available_at=now()");dispatcher.dispatchOne();}
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox",String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT last_error FROM notification_outbox",String.class)).isEqualTo("DELIVERY_ERROR");assertThat(dispatcher.dispatchOne()).isFalse();verify(provider,times(6)).send(any());
    }
    @Test void lifecycleDeduplicationRetainsRefundStatusChanges() {
        new TransactionTemplate(manager).executeWithoutResult(s->{
            for(String type:java.util.List.of("ORDER_APPROVED","ORDER_REJECTED","ORDER_CANCELLED")) {
                var event=new NotificationRequested(type,1,null,"VIETQR","REFUND_PENDING");outbox.enqueue(event);outbox.enqueue(event);
            }
            outbox.enqueue(new NotificationRequested("ORDER_CANCELLED",1,null,"VIETQR","REFUNDED"));
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox",Integer.class)).isEqualTo(4);
    }
    @Test void outboxWriteFailureRollsBackBusinessTransaction() {
        var proof=begin();jdbc.execute("ALTER TABLE notification_outbox ADD CONSTRAINT test_reject CHECK (order_id<>1)");
        try {
            assertThatThrownBy(()->payments.confirm(proof)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM payment_transactions",String.class)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("PENDING");
        } finally {jdbc.execute("ALTER TABLE notification_outbox DROP CONSTRAINT test_reject");}
    }
    @Test void upgradeFromV9PreservesOrdersAndIsRepeatable() {
        var old=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas("upgrade_notifications").defaultSchema("upgrade_notifications").target("9").load();old.migrate();
        jdbc.update("INSERT INTO upgrade_notifications.orders(sub_total,tax,shipping_fee,total_payment) VALUES (100,10,22000,22110)");
        var next=org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas("upgrade_notifications").defaultSchema("upgrade_notifications").load();
        assertThat(next.migrate().migrationsExecuted).isEqualTo(1);assertThat(next.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upgrade_notifications.orders",Integer.class)).isEqualTo(1);
    }
    @Test void failedProviderDoesNotBlockAnotherChannel() throws Exception {
        payments.confirm(begin());jdbc.update("INSERT INTO notification_outbox(event_id,event_key,channel,order_id,payload,created_at) SELECT ?,event_key,'SECOND',order_id,payload,created_at+interval '1 second' FROM notification_outbox",java.util.UUID.randomUUID());
        var second=mock(NotificationProvider.class);when(second.channel()).thenReturn("SECOND");when(second.available()).thenReturn(true);
        doThrow(new NotificationProvider.DeliveryFailure("HTTP_503",true)).when(provider).send(any());
        var dispatch=new NotificationDispatcher(jdbc,new ObjectMapper(),java.util.List.of(provider,second));
        var tx=new TransactionTemplate(manager);tx.executeWithoutResult(s->dispatch.dispatchOne());tx.executeWithoutResult(s->dispatch.dispatchOne());
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox WHERE channel='EMAIL'",String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox WHERE channel='SECOND'",String.class)).isEqualTo("SENT");verify(second).send(any());
    }
}
