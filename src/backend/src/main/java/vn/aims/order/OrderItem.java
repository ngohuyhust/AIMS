package vn.aims.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import vn.aims.product.Product;

@Entity @Table(name="order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="order_item_id") Integer orderItemID;
    @Column(nullable=false) int quantity;
    @Column(name="unit_price",nullable=false,precision=12,scale=2) BigDecimal unitPrice;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id") Order order;
    @ManyToOne @JoinColumn(name="product_id") Product product;
    protected OrderItem() {}
}
