package vn.aims.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="invoices")
public class Invoice {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="invoice_id") Integer invoiceID;
    @Column(name="total_exclude_vat",nullable=false,precision=12,scale=2) BigDecimal totalExcludeVAT;
    @Column(name="total_include_vat",nullable=false,precision=12,scale=2) BigDecimal totalIncludeVAT;
    @Column(name="shipping_fee",nullable=false,precision=12,scale=2) BigDecimal shippingFee;
    @Column(name="total_payment",nullable=false,precision=12,scale=2) BigDecimal totalPayment;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id",unique=true) Order order;
    protected Invoice() {}
}
