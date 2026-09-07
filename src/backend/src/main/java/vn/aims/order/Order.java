package vn.aims.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="orders")
public class Order {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="order_id") Integer orderID;
    @Column(name="sub_total",nullable=false,precision=12,scale=2) BigDecimal subTotal;
    @Column(nullable=false,precision=12,scale=2) BigDecimal tax;
    @Column(name="shipping_fee",nullable=false,precision=12,scale=2) BigDecimal shippingFee;
    @Column(name="total_payment",nullable=false,precision=12,scale=2) BigDecimal totalPayment;
    @Column(nullable=false,length=50) String status;
    @Column(name="customer_access_token",length=64,unique=true) String customerAccessToken;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    @OneToMany(mappedBy="order",cascade=CascadeType.PERSIST) @OrderBy("orderItemID ASC") List<OrderItem> orderItems=new ArrayList<>();
    @OneToOne(mappedBy="order",cascade=CascadeType.PERSIST) DeliveryInfo deliveryInfo;
    @OneToOne(mappedBy="order",cascade=CascadeType.PERSIST) Invoice invoice;
    protected Order() {}
}
