package vn.aims.payment;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    record OrderState(int id,String status,BigDecimal total) {}
    private final JdbcTemplate jdbc;
    private static final RowMapper<PaymentView> ROW=(row,index)->new PaymentView(row.getInt("transaction_id"),row.getObject("order_id",Integer.class),
            row.getString("method"),row.getBigDecimal("amount"),row.getString("transaction_content"),PaymentStatus.valueOf(row.getString("status")),row.getTimestamp("created_at").toInstant());
    public PaymentRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    OrderState lockOrder(int id) {
        var rows=jdbc.query("SELECT order_id,status,total_payment FROM orders WHERE order_id=? FOR UPDATE",
            (row,index)->new OrderState(row.getInt(1),row.getString(2),row.getBigDecimal(3)),id);
        if(rows.isEmpty()) throw new PaymentException(404,"Order with ID "+id+" not found");return rows.getFirst();
    }
    boolean hasLifecycleOperation(int id) {return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM order_lifecycle_operations WHERE order_id=?)",Boolean.class,id));}
    Optional<PaymentView> find(int id,boolean lock) {
        return jdbc.query("SELECT * FROM payment_transactions WHERE transaction_id=?"+(lock?" FOR UPDATE":""),ROW,id).stream().findFirst();
    }
    List<PaymentView> active(int orderId) {
        return jdbc.query("SELECT * FROM payment_transactions WHERE order_id=? AND status IN ('PENDING','SUCCESS') ORDER BY created_at DESC,transaction_id DESC",ROW,orderId);
    }
    Optional<PaymentView> latest(int orderId) {
        return jdbc.query("SELECT * FROM payment_transactions WHERE order_id=? ORDER BY created_at DESC,transaction_id DESC LIMIT 1",ROW,orderId).stream().findFirst();
    }
    PaymentView create(int orderId,BigDecimal amount,String method,String content) {
        return jdbc.queryForObject("INSERT INTO payment_transactions(order_id,amount,method,transaction_content,status) VALUES (?,?,?,?,'PENDING') RETURNING *",ROW,orderId,amount,method,content);
    }
    boolean transition(int id,PaymentStatus expected,PaymentStatus next) {
        if(!expected.canTransitionTo(next)) throw new IllegalArgumentException("Unsupported payment transition");
        return jdbc.update("UPDATE payment_transactions SET status=? WHERE transaction_id=? AND status=?",next.name(),id,expected.name())==1;
    }
    void markOrderPaid(int id) {
        if(jdbc.update("UPDATE orders SET status='PENDING_PROCESSING',updated_at=now() WHERE order_id=? AND status='PENDING'",id)!=1)
            throw new PaymentException(409,"Order cannot accept payment from its current status");
    }
}
