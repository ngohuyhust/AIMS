package vn.aims.notification;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class NotificationOutbox {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final List<NotificationProvider> providers;
    public NotificationOutbox(JdbcTemplate jdbc,ObjectMapper json,List<NotificationProvider> providers) {this.jdbc=jdbc;this.json=json;this.providers=providers;}
    @EventListener @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(NotificationRequested event) {
        if(!Set.of("ORDER_PAYMENT_SUCCEEDED","ORDER_APPROVED","ORDER_REJECTED","ORDER_CANCELLED").contains(event.type())) throw new IllegalArgumentException("Unknown notification type");
        String snapshot=jdbc.queryForObject("""
            SELECT jsonb_build_object('order',to_jsonb(o),'delivery',to_jsonb(d),'invoice',to_jsonb(i),'payment',to_jsonb(p))::text
            FROM orders o LEFT JOIN delivery_info d ON d.order_id=o.order_id LEFT JOIN invoices i ON i.order_id=o.order_id
            LEFT JOIN payment_transactions p ON p.order_id=o.order_id AND p.transaction_id=coalesce(?::integer,
              (SELECT transaction_id FROM payment_transactions WHERE order_id=o.order_id AND status='SUCCESS' ORDER BY created_at DESC,transaction_id DESC LIMIT 1))
            WHERE o.order_id=?
            """,String.class,event.paymentTransactionId(),event.orderId());
        try {
            var payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(snapshot);
            payload.put("type",event.type()).put("refundMethod",event.refundMethod()).put("refundStatus",event.refundStatus());
            String key=event.type()+":"+event.orderId()+":"+event.paymentTransactionId()+":"+event.refundStatus();
            for(var provider:providers) jdbc.update("INSERT INTO notification_outbox(event_id,event_key,channel,order_id,payload) VALUES (?,?,?,?,?::jsonb) ON CONFLICT(event_key,channel) DO NOTHING",UUID.randomUUID(),key,provider.channel(),event.orderId(),payload.toString());
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException("Cannot snapshot notification");}
    }
}
