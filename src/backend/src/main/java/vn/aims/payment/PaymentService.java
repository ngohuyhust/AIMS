package vn.aims.payment;

import java.math.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

/** Owns shared payment state. Gateways call this only after authenticating their provider results. */
@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final ApplicationEventPublisher events;
    public PaymentService(PaymentRepository payments,ApplicationEventPublisher events) { this.payments=payments;this.events=events; }
    @Transactional
    public PaymentView begin(int orderId,String method,BigDecimal requestedAmount,String content) {
        validateMethod(method);
        var order=payments.lockOrder(orderId);
        if(!order.status().equals("PENDING")) throw new PaymentException(409,"Order cannot accept payment from its current status");
        var amount=wholeVnd(order.total());
        if(amount.signum()<=0 || requestedAmount==null || requestedAmount.compareTo(amount)!=0)
            throw new PaymentException(400,"Payment amount does not match order total");
        var active=payments.active(orderId);
        if(!active.isEmpty()) {
            // A stable attempt ID is also the future provider request-id; no duplicate charge attempts.
            if(active.size()==1 && active.getFirst().status()==PaymentStatus.PENDING && active.getFirst().method().equals(method)
                    && active.getFirst().amount().compareTo(amount)==0) return active.getFirst();
            throw new PaymentException(409,"Order already has an active payment");
        }
        return payments.create(orderId,amount,method,content);
    }
    @Transactional
    public boolean confirm(PaymentConfirmation proof) {
        var order=payments.lockOrder(proof.orderId());
        var payment=required(proof.paymentTransactionId(),true);match(payment,proof);
        if(payment.status()==PaymentStatus.SUCCESS || payment.status()==PaymentStatus.REFUNDED) return false;
        if(payment.status()!=PaymentStatus.PENDING || !order.status().equals("PENDING"))
            throw new PaymentException(409,"Payment cannot be confirmed from its current status");
        if(wholeVnd(order.total()).compareTo(payment.amount())!=0) throw new PaymentException(409,"Order total changed since payment was created");
        if(!payments.transition(payment.transactionID(),PaymentStatus.PENDING,PaymentStatus.SUCCESS)) return false;
        payments.markOrderPaid(order.id());
        var event=new PaymentConfirmed(order.id(),payment.transactionID());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { events.publishEvent(event); }
        });
        return true;
    }
    @Transactional
    public boolean fail(int transactionId) {
        var payment=required(transactionId,false);
        if(payment.orderId()==null) throw new PaymentException(409,"Payment has no associated order");
        payments.lockOrder(payment.orderId());
        payment=required(transactionId,true);
        return payments.transition(payment.transactionID(),PaymentStatus.PENDING,PaymentStatus.FAILED);
    }
    /** Records a verified refund result only; actual refund execution/eligibility belongs to later modules. */
    @Transactional
    public boolean markRefunded(PaymentConfirmation proof) {
        payments.lockOrder(proof.orderId());var payment=required(proof.paymentTransactionId(),true);match(payment,proof);
        if(payment.status()==PaymentStatus.REFUNDED) return false;
        if(payment.status()!=PaymentStatus.SUCCESS) throw new PaymentException(409,"Only successful payments can be refunded");
        return payments.transition(payment.transactionID(),PaymentStatus.SUCCESS,PaymentStatus.REFUNDED);
    }
    @Transactional(readOnly=true)
    public Optional<PaymentView> find(int id) { return payments.find(id,false); }
    @Transactional(readOnly=true)
    public Optional<PaymentView> latest(int orderId) { return payments.latest(orderId); }
    public static BigDecimal wholeVnd(BigDecimal amount) { return amount.setScale(0,RoundingMode.HALF_UP); }
    private PaymentView required(int id,boolean lock) { return payments.find(id,lock).orElseThrow(()->new PaymentException(404,"Payment transaction "+id+" not found")); }
    private void match(PaymentView payment,PaymentConfirmation proof) {
        if(!Objects.equals(payment.orderId(),proof.orderId()) || !payment.method().equals(proof.method()) || proof.amount()==null || payment.amount().compareTo(proof.amount())!=0)
            throw new PaymentException(400,"Payment confirmation does not match its order, method or amount");
    }
    private void validateMethod(String method) {
        if(method==null || !method.matches("[A-Z][A-Z0-9_]{0,44}")) throw new PaymentException(400,"Invalid payment method");
    }
}
