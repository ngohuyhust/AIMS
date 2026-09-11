package vn.aims.order.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import vn.aims.product.entity.Product;

@Entity @Table(name="order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="order_item_id") private Integer orderItemID;
    @Column(nullable=false) private int quantity;
    @Column(name="unit_price",nullable=false,precision=12,scale=2) private BigDecimal unitPrice;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id") private Order order;
    @ManyToOne @JoinColumn(name="product_id") private Product product;
    public OrderItem() {}
    public Integer getOrderItemID() {return orderItemID;}
    public int getQuantity() {return quantity;}
    public void setQuantity(int value) {quantity=value;}
    public BigDecimal getUnitPrice() {return unitPrice;}
    public void setUnitPrice(BigDecimal value) {unitPrice=value;}
    public void setOrder(Order value) {order=value;}
    public Product getProduct() {return product;}
    public void setProduct(Product value) {product=value;}
}
