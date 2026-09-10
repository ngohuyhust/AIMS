package vn.aims.order.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.aims.order.domain.Order;

@Repository
public class OrderRepository {
    public record Stock(int id,String status,int quantity,BigDecimal price,double weight) {}
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    public OrderRepository(EntityManager em,JdbcTemplate jdbc) { this.em=em;this.jdbc=jdbc; }
    public Map<Integer,Stock> lockProducts(Collection<Integer> ids) {
        var result=new HashMap<Integer,Stock>();
        // Same ascending product lock order as ProductAdminService, independent of cart line order.
        for(int id:new TreeSet<>(ids)) jdbc.query("SELECT product_id,status,quantity_in_stock,current_price,weight FROM products WHERE product_id=? FOR UPDATE",
            (org.springframework.jdbc.core.RowCallbackHandler) row->result.put(id,new Stock(id,row.getString("status"),row.getInt("quantity_in_stock"),row.getBigDecimal("current_price"),row.getDouble("weight"))),id);
        return result;
    }
    public void reserve(int id,int quantity) { jdbc.update("UPDATE products SET quantity_in_stock=quantity_in_stock-?,updated_at=now() WHERE product_id=?",quantity,id); }
    public vn.aims.product.Product product(int id) { return em.getReference(vn.aims.product.Product.class,id); }
    public Order save(Order order) {
        if(order.getOrderID()==null) em.persist(order);
        em.flush();int id=order.getOrderID();em.clear();return em.find(Order.class,id);
    }
    public Order find(int id,boolean lock) {
        if(lock) {
            // Lock just the order row, not nullable outer joins in its full relation graph.
            jdbc.query("SELECT order_id FROM orders WHERE order_id=? FOR UPDATE",(org.springframework.jdbc.core.RowCallbackHandler) row->{},id);
        }
        return em.find(Order.class,id);
    }
    public void persist(Object value) { em.persist(value); }
    public boolean hasActivePayment(int id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM payment_transactions WHERE order_id=? AND status IN ('PENDING','SUCCESS')) OR EXISTS(SELECT 1 FROM order_lifecycle_operations WHERE order_id=? AND status='PENDING')",Boolean.class,id,id));
    }
    public String paymentMethod(int id) {
        var rows=jdbc.queryForList("SELECT method FROM payment_transactions WHERE order_id=? AND status='SUCCESS' ORDER BY created_at DESC,transaction_id DESC LIMIT 1",String.class,id);
        return rows.isEmpty()?null:rows.getFirst();
    }
}
