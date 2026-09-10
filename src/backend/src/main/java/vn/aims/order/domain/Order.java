package vn.aims.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="orders")
public class Order {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="order_id") private Integer orderID;
    @Column(name="sub_total",nullable=false,precision=12,scale=2) private BigDecimal subTotal;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal tax;
    @Column(name="shipping_fee",nullable=false,precision=12,scale=2) private BigDecimal shippingFee;
    @Column(name="total_payment",nullable=false,precision=12,scale=2) private BigDecimal totalPayment;
    @Column(nullable=false,length=50) private String status;
    @Column(name="customer_access_token",length=64,unique=true) private String customerAccessToken;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @OneToMany(mappedBy="order",cascade=CascadeType.PERSIST) @OrderBy("orderItemID ASC") private List<OrderItem> orderItems=new ArrayList<>();
    @OneToOne(mappedBy="order",cascade=CascadeType.PERSIST) private DeliveryInfo deliveryInfo;
    @OneToOne(mappedBy="order",cascade=CascadeType.PERSIST) private Invoice invoice;
    public Order() {}
    public Integer getOrderID() {return orderID;}
    public BigDecimal getSubTotal() {return subTotal;}
    public void setSubTotal(BigDecimal value) {subTotal=value;}
    public BigDecimal getTax() {return tax;}
    public void setTax(BigDecimal value) {tax=value;}
    public BigDecimal getShippingFee() {return shippingFee;}
    public void setShippingFee(BigDecimal value) {shippingFee=value;}
    public BigDecimal getTotalPayment() {return totalPayment;}
    public void setTotalPayment(BigDecimal value) {totalPayment=value;}
    public String getStatus() {return status;}
    public void setStatus(String value) {status=value;}
    public String getCustomerAccessToken() {return customerAccessToken;}
    public void setCustomerAccessToken(String value) {customerAccessToken=value;}
    public Instant getCreatedAt() {return createdAt;}
    public void setCreatedAt(Instant value) {createdAt=value;}
    public Instant getUpdatedAt() {return updatedAt;}
    public void setUpdatedAt(Instant value) {updatedAt=value;}
    public List<OrderItem> getOrderItems() {return orderItems;}
    public DeliveryInfo getDeliveryInfo() {return deliveryInfo;}
    public void setDeliveryInfo(DeliveryInfo value) {deliveryInfo=value;}
    public Invoice getInvoice() {return invoice;}
    public void setInvoice(Invoice value) {invoice=value;}
}
