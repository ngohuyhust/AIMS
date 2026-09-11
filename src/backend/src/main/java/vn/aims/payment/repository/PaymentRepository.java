package vn.aims.payment.repository;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.aims.payment.dto.*;
import vn.aims.payment.event.*;
import vn.aims.payment.exception.*;
import vn.aims.payment.service.*;
import vn.aims.payment.entity.PaymentStatus;

@Repository
public class PaymentRepository {
    public record OrderState(int id,String status,BigDecimal total) {}
    private final JdbcTemplate jdbc;
    private static final RowMapper<PaymentView> ROW=(row,index)->new PaymentView(row.getInt("transaction_id"),row.getObject("order_id",Integer.class),
            row.getString("method"),row.getBigDecimal("amount"),row.getString("transaction_content"),PaymentStatus.valueOf(row.getString("status")),row.getTimestamp("created_at").toInstant());
    public PaymentRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public OrderState lockOrder(int id) {
        var rows=jdbc.query("SELECT order_id,status,total_payment FROM orders WHERE order_id=? FOR UPDATE",
            (row,index)->new OrderState(row.getInt(1),row.getString(2),row.getBigDecimal(3)),id);
        if(rows.isEmpty()) throw new PaymentException(404,"Order with ID "+id+" not found");return rows.getFirst();
    }
    public boolean hasLifecycleOperation(int id) {return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM order_lifecycle_operations WHERE order_id=?)",Boolean.class,id));}
    public Optional<PaymentView> find(int id,boolean lock) {
        return jdbc.query("SELECT * FROM payment_transactions WHERE transaction_id=?"+(lock?" FOR UPDATE":""),ROW,id).stream().findFirst();
    }
    public List<PaymentView> active(int orderId) {
        return jdbc.query("SELECT * FROM payment_transactions WHERE order_id=? AND status IN ('PENDING','SUCCESS') ORDER BY created_at DESC,transaction_id DESC",ROW,orderId);
    }
    public Optional<PaymentView> latest(int orderId) {
        return jdbc.query("SELECT * FROM payment_transactions WHERE order_id=? ORDER BY created_at DESC,transaction_id DESC LIMIT 1",ROW,orderId).stream().findFirst();
    }
    public PaymentView create(int orderId,BigDecimal amount,String method,String content) {
        return jdbc.queryForObject("INSERT INTO payment_transactions(order_id,amount,method,transaction_content,status) VALUES (?,?,?,?,'PENDING') RETURNING *",ROW,orderId,amount,method,content);
    }
    public boolean transition(int id,PaymentStatus expected,PaymentStatus next) {
        if(!expected.canTransitionTo(next)) throw new IllegalArgumentException("Unsupported payment transition");
        return jdbc.update("UPDATE payment_transactions SET status=? WHERE transaction_id=? AND status=?",next.name(),id,expected.name())==1;
    }
    public void markOrderPaid(int id) {
        if(jdbc.update("UPDATE orders SET status='PENDING_PROCESSING',updated_at=now() WHERE order_id=? AND status='PENDING'",id)!=1)
            throw new PaymentException(409,"Order cannot accept payment from its current status");
    }
}
