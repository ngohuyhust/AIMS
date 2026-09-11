package vn.aims.vietqr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import vn.aims.payment.entity.PaymentTransaction;

@Entity @Table(name="vietqr_transactions")
public class VietqrTransaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="vietqr_transaction_id") Integer vietqrTransactionID;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="transaction_id",unique=true) PaymentTransaction paymentTransaction;
    @Column(name="order_id",nullable=false) int orderId;
    @Column(nullable=false,precision=12,scale=2) BigDecimal amount;
    @Column(nullable=false,length=23) String content;
    @Column(name="qr_code",columnDefinition="text") String qrCode;
    @Column(name="qr_link",columnDefinition="text") String qrLink;
    @Column(name="transaction_id_ref",length=100) String transactionId;
    @Column(name="transaction_ref_id",length=100) String transactionRefId;
    @Column(name="expired_at",nullable=false) Instant expiredAt;
    @Column(name="paid_at") Instant paidAt;
    @Column(nullable=false,length=50) String status;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) @Column(name="raw_callback",columnDefinition="jsonb") JsonNode rawCallback;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    protected VietqrTransaction() {}
}
