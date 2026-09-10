package vn.aims.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="invoices")
public class Invoice {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="invoice_id") private Integer invoiceID;
    @Column(name="total_exclude_vat",nullable=false,precision=12,scale=2) private BigDecimal totalExcludeVAT;
    @Column(name="total_include_vat",nullable=false,precision=12,scale=2) private BigDecimal totalIncludeVAT;
    @Column(name="shipping_fee",nullable=false,precision=12,scale=2) private BigDecimal shippingFee;
    @Column(name="total_payment",nullable=false,precision=12,scale=2) private BigDecimal totalPayment;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id",unique=true) private Order order;
    public Invoice() {}
    public Integer getInvoiceID() {return invoiceID;}
    public BigDecimal getTotalExcludeVAT() {return totalExcludeVAT;}
    public void setTotalExcludeVAT(BigDecimal value) {totalExcludeVAT=value;}
    public BigDecimal getTotalIncludeVAT() {return totalIncludeVAT;}
    public void setTotalIncludeVAT(BigDecimal value) {totalIncludeVAT=value;}
    public BigDecimal getShippingFee() {return shippingFee;}
    public void setShippingFee(BigDecimal value) {shippingFee=value;}
    public BigDecimal getTotalPayment() {return totalPayment;}
    public void setTotalPayment(BigDecimal value) {totalPayment=value;}
    public Instant getCreatedAt() {return createdAt;}
    public void setCreatedAt(Instant value) {createdAt=value;}
    public void setOrder(Order order) {this.order=order;}
}
