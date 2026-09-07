package vn.aims.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import vn.aims.order.Order;

@Entity @Table(name="payment_transactions")
public class PaymentTransaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="transaction_id") Integer transactionID;
    @Column(nullable=false,length=45) String method;
    @Column(nullable=false,precision=12,scale=2) BigDecimal amount;
    @Column(name="transaction_content",columnDefinition="text") String transactionContent;
    @Column(nullable=false,length=50) @Enumerated(EnumType.STRING) PaymentStatus status;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id") Order order;
    protected PaymentTransaction() {}
}
