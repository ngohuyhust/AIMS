package vn.aims.paypal.entity;

import jakarta.persistence.*;
import vn.aims.payment.entity.PaymentTransaction;

@Entity @Table(name="paypal_transactions")
public class PaypalTransaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="paypal_transaction_id") Integer paypalTransactionID;
    @Column(name="paypal_order_id",length=100) String paypalOrderID;
    @Column(name="paypal_capture_id",length=100) String paypalCaptureID;
    @Column(name="payer_id",length=100) String payerID;
    @Column(length=50) String status;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="transaction_id",unique=true) PaymentTransaction paymentTransaction;
    protected PaypalTransaction() {}
}
