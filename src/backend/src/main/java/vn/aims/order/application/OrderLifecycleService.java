package vn.aims.order.application;

import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.context.ApplicationEventPublisher;
import vn.aims.order.domain.Order;
import vn.aims.order.infrastructure.*;
import vn.aims.payment.*;
import vn.aims.paypal.PaypalService;

@Service @Transactional(propagation=Propagation.NEVER)
public class OrderLifecycleService {
    private final OrderRepository orders;private final OrderLifecycleStore store;private final OrderAccess access;
    private final PaypalService paypal;private final PaymentService payments;private final ApplicationEventPublisher events;private final TransactionTemplate tx;
    public OrderLifecycleService(OrderRepository orders,OrderLifecycleStore store,OrderAccess access,PaypalService paypal,PaymentService payments,ApplicationEventPublisher events,PlatformTransactionManager manager) {
        this.orders=orders;this.store=store;this.access=access;this.paypal=paypal;this.payments=payments;this.events=events;tx=new TransactionTemplate(manager);
    }
    private <T> T transaction(Supplier<T> action) {return tx.execute(s->action.get());}
    public Map<String,Object> approve(int id,String authorization) {
        manager(authorization);return transaction(()->{
            var order=required(id);allowed(order,"approve");
            if(store.operation(id)!=null || store.refundStarted(id)) throw new OrderError(409,"Order has a cancellation or refund in progress");
            var money=store.payments(id);noPending(money);
            if(money.stream().anyMatch(p->p.status().equals("REFUNDED"))) throw new OrderError(409,"Refunded payment cannot be approved");
            order.setStatus("APPROVED");order.setUpdatedAt(java.time.Instant.now());var result=OrderResponse.from(orders.save(order),false);
            publish(new OrderLifecycleEvent("ORDER_APPROVED",id,null,null,null));return result;
        });
    }
    public Map<String,Object> close(int id,String action,String token,String authorization,boolean customer) {
        if(!Set.of("CANCEL","REJECT").contains(action)) throw new IllegalArgumentException("Invalid lifecycle action");
        if(!customer) manager(authorization);
        boolean refund=transaction(()->{
            var order=required(id);authorize(order,id,token,customer);var money=store.payments(id);
            if(customer && (order.getStatus().equals("PENDING_PROCESSING") || money.stream().anyMatch(p->!p.status().equals("PENDING")))) throw new OrderError(403,"Only a product manager can cancel a paid order");
            var operation=store.operation(id);
            if(operation!=null) {
                if(!operation.action().equals(action)) throw new OrderError(400,"Order already has a different cancellation action");
                if(operation.status().equals("COMPLETED")) return false;
            }
            allowed(order,action.toLowerCase(Locale.ROOT));noPending(money);
            if(money.size()>1) throw new OrderError(409,"Ambiguous payment history requires reconciliation");
            var payment=money.isEmpty()?null:money.getFirst();
            if(order.getStatus().equals("PENDING_PROCESSING") && payment==null) throw new OrderError(409,"Paid order has no payment to reconcile");
            if(payment!=null && !Set.of("PAYPAL","VIETQR").contains(payment.method())) throw new OrderError(409,"Payment method requires manual reconciliation");
            if(operation==null) store.begin(id,action,payment);
            else if(!Objects.equals(operation.paymentId(),payment==null?null:payment.id())) throw new OrderError(409,"Payment changed during cancellation");
            return payment!=null && payment.method().equals("PAYPAL") && payment.status().equals("SUCCESS");
        });
        // Never hold an order/product lock across provider I/O. Durable action blocks competing approval.
        if(refund) paypal.execute("REFUND",id,null,null,authorization);
        return transaction(()->{
            var order=required(id);authorize(order,id,token,customer);var operation=store.operation(id);
            if(operation==null || !operation.action().equals(action)) throw new OrderError(409,"Cancellation action changed");
            if(operation.status().equals("COMPLETED")) return OrderResponse.from(order,customer);
            allowed(order,action.toLowerCase(Locale.ROOT));
            var payment=operation.paymentId()==null?null:payments.find(operation.paymentId()).orElseThrow(()->new OrderError(409,"Payment disappeared"));
            if(payment!=null && payment.status()!=PaymentStatus.REFUNDED && !(payment.method().equals("VIETQR") && payment.status()==PaymentStatus.SUCCESS)) throw new OrderError(409,"Refund is not confirmed");
            store.restore(id);order.setStatus(payment!=null && payment.status()!=PaymentStatus.REFUNDED?"REFUND_PENDING":action.equals("CANCEL")?"CANCELLED":"REJECTED");
            order.setUpdatedAt(java.time.Instant.now());store.complete(id);var result=OrderResponse.from(orders.save(order),customer);
            publish(new OrderLifecycleEvent(action.equals("CANCEL")?"ORDER_CANCELLED":"ORDER_REJECTED",id,operation.paymentId(),operation.method(),payment==null?"No payment refund required":payment.status()==PaymentStatus.REFUNDED?"REFUNDED":"REFUND_PENDING"));return result;
        });
    }
    public Map<String,Object> confirmVietqrRefund(int id,String authorization) {
        manager(authorization);return transaction(()->{
            var order=required(id);var candidates=store.payments(id).stream().filter(p->p.method().equals("VIETQR") && !p.status().equals("PENDING")).toList();
            if(candidates.size()!=1) throw new OrderError(400,"Order #"+id+" does not have a successful VietQR transaction to refund");
            var payment=candidates.getFirst();
            if(order.getStatus().equals("REFUNDED") && payment.status().equals("REFUNDED")) return OrderResponse.from(order,false);
            if(!order.getStatus().equals("REFUND_PENDING")) throw new OrderError(400,"Order #"+id+" is in status "+order.getStatus()+", not REFUND_PENDING");
            payments.markRefunded(new PaymentConfirmation(payment.id(),id,"VIETQR",payment.amount()));
            order.setStatus("REFUNDED");order.setUpdatedAt(java.time.Instant.now());var result=OrderResponse.from(orders.save(order),false);
            publish(new OrderLifecycleEvent("ORDER_CANCELLED",id,payment.id(),"VIETQR","REFUNDED"));return result;
        });
    }
    private Order required(int id) {var order=orders.find(id,true);if(order==null) throw new OrderError(404,"Order with ID "+id+" not found");return order;}
    private void authorize(Order order,int id,String token,boolean customer) {if(customer) {if(token==null || token.isBlank()) throw new OrderError(400,"Missing customer order access token");access.require(order,id,token,false);}}
    private void manager(String authorization) {if(!access.manager(authorization)) throw new OrderError(403,"Product manager role is required");}
    private void noPending(List<OrderLifecycleStore.Payment> money) {if(money.stream().anyMatch(p->p.status().equals("PENDING"))) throw new OrderError(409,"Payment outcome must be resolved before changing order status");}
    private void allowed(Order order,String action) {
        if(!Set.of("PENDING","PENDING_PROCESSING").contains(order.getStatus())) throw new OrderError(400,"Order "+order.getOrderID()+" cannot be "+switch(action){case "approve"->"approved";case "reject"->"rejected";default->"cancelled";}+" from status "+order.getStatus());
    }
    private void publish(OrderLifecycleEvent event) {events.publishEvent(new vn.aims.notification.NotificationRequested(event.type(),event.orderId(),event.paymentTransactionId(),event.refundMethod(),event.refundStatus()));TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){events.publishEvent(event);}});}
}
