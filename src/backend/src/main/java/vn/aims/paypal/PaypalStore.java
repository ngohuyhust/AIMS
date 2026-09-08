package vn.aims.paypal;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.aims.payment.PaymentException;

@Repository
public class PaypalStore {
    record OrderRow(int id,String status,BigDecimal total,String token) {}
    record Binding(int transactionId,String method,BigDecimal amount,String paymentStatus,String gatewayId,String captureId,String status) {}
    record Operation(UUID id,Instant createdAt,JsonNode response) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public PaypalStore(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    OrderRow order(int id) {
        var rows=jdbc.query("SELECT order_id,status,total_payment,customer_access_token FROM orders WHERE order_id=? FOR UPDATE",
            (r,n)->new OrderRow(r.getInt(1),r.getString(2),r.getBigDecimal(3),r.getString(4)),id);
        return rows.isEmpty()?null:rows.getFirst();
    }
    void placeholder(int id) { jdbc.update("INSERT INTO paypal_transactions(transaction_id,status) VALUES (?,'CREATING') ON CONFLICT(transaction_id) DO NOTHING",id); }
    Binding binding(int orderId) {
        var rows=jdbc.query("""
            SELECT p.transaction_id,p.method,p.amount,p.status,t.paypal_order_id,t.paypal_capture_id,t.status
            FROM payment_transactions p JOIN paypal_transactions t USING(transaction_id)
            WHERE p.order_id=? AND p.method='PAYPAL' ORDER BY p.created_at DESC,p.transaction_id DESC LIMIT 1
            """,(r,n)->new Binding(r.getInt(1),r.getString(2),r.getBigDecimal(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7)),orderId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    void operation(int transactionId,String operation) {
        jdbc.update("INSERT INTO paypal_operations(operation_id,transaction_id,operation) VALUES (?,?,?) ON CONFLICT(transaction_id,operation) DO NOTHING",UUID.randomUUID(),transactionId,operation);
    }
    Operation lockOperation(int transactionId,String operation) {
        return jdbc.queryForObject("SELECT operation_id,created_at,response FROM paypal_operations WHERE transaction_id=? AND operation=? FOR UPDATE",
            (r,n)->{ try {return new Operation(r.getObject(1,UUID.class),r.getTimestamp(2).toInstant(),r.getString(3)==null?null:json.readTree(r.getString(3)));}
                catch(Exception error) {throw new PaymentException(500,"Invalid stored PayPal operation");} },transactionId,operation);
    }
    void response(UUID id,JsonNode response) { jdbc.update("UPDATE paypal_operations SET response=?::jsonb WHERE operation_id=?",response.toString(),id); }
    void created(int id,String gatewayId,String status) { jdbc.update("UPDATE paypal_transactions SET paypal_order_id=?,status=? WHERE transaction_id=?",gatewayId,status,id); }
    void captured(int id,String captureId) { jdbc.update("UPDATE paypal_transactions SET paypal_capture_id=?,status='COMPLETED' WHERE transaction_id=?",captureId,id); }
    void refunded(int id) { jdbc.update("UPDATE paypal_transactions SET status='REFUNDED' WHERE transaction_id=?",id); }
}
