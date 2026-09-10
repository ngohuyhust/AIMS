package vn.aims.order.infrastructure;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderLifecycleStore {
    public record Payment(int id,String method,String status,BigDecimal amount) {}
    public record Operation(String action,Integer paymentId,String method,String status) {}
    private final JdbcTemplate jdbc;
    public OrderLifecycleStore(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public List<Payment> payments(int order) {return jdbc.query("SELECT transaction_id,method,status,amount FROM payment_transactions WHERE order_id=? AND status IN ('PENDING','SUCCESS','REFUNDED') ORDER BY created_at DESC,transaction_id DESC",(r,n)->new Payment(r.getInt(1),r.getString(2),r.getString(3),r.getBigDecimal(4)),order);}
    public Operation operation(int order) {var rows=jdbc.query("SELECT action,transaction_id,refund_method,status FROM order_lifecycle_operations WHERE order_id=?",(r,n)->new Operation(r.getString(1),r.getObject(2,Integer.class),r.getString(3),r.getString(4)),order);return rows.isEmpty()?null:rows.getFirst();}
    public void begin(int order,String action,Payment payment) {jdbc.update("INSERT INTO order_lifecycle_operations(order_id,action,transaction_id,refund_method) VALUES (?,?,?,?)",order,action,payment==null?null:payment.id(),payment==null?null:payment.method());}
    public void complete(int order) {jdbc.update("UPDATE order_lifecycle_operations SET status='COMPLETED',completed_at=now() WHERE order_id=?",order);}
    public boolean refundStarted(int order) {return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM paypal_operations o JOIN payment_transactions p USING(transaction_id) WHERE p.order_id=? AND o.operation='REFUND')",Boolean.class,order));}
    public void restore(int order) {
        var rows=jdbc.queryForList("SELECT product_id,sum(quantity)::bigint AS quantity FROM order_items WHERE order_id=? AND product_id IS NOT NULL GROUP BY product_id ORDER BY product_id",order);
        for(var row:rows) {int id=((Number)row.get("product_id")).intValue();jdbc.query("SELECT product_id FROM products WHERE product_id=? FOR UPDATE",(org.springframework.jdbc.core.RowCallbackHandler)r->{},id);
            jdbc.update("UPDATE products SET quantity_in_stock=quantity_in_stock+?,updated_at=now() WHERE product_id=?",row.get("quantity"),id);}
    }
}
