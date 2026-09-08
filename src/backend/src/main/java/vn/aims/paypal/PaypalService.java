package vn.aims.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import vn.aims.auth.JwtTokens;
import vn.aims.payment.*;
import vn.aims.payment.gateway.*;

/** Commit intent, journal remote result, then atomically apply verified financial effects. */
@Service @Transactional(propagation=Propagation.NEVER)
public class PaypalService {
    record Prepared(GatewayRequest request,String gatewayId,String captureId) {}
    private final PaypalStore store;
    private final PaymentService payments;
    private final CreditCardGateway gateway;
    private final PaypalApiClient client;
    private final JwtTokens jwt;
    private final TransactionTemplate tx;
    public PaypalService(PaypalStore store,PaymentService payments,CreditCardGateway gateway,PaypalApiClient client,JwtTokens jwt,PlatformTransactionManager manager) {
        this.store=store;this.payments=payments;this.gateway=gateway;this.client=client;this.jwt=jwt;tx=new TransactionTemplate(manager);
    }
    private <T> T transaction(Supplier<T> action) { return tx.execute(status->action.get()); }
    public JsonNode execute(String operation,int orderId,String submittedGatewayId,String token,String authorization) {
        Prepared prepared=transaction(()->{
            var order=store.order(orderId);authorize(order,token,authorization,operation.equals("REFUND"));client.configured();
            if(operation.equals("CREATE")) {
                var payment=payments.begin(orderId,"PAYPAL",PaymentService.wholeVnd(order.total()),"PAYPAL payment for order "+orderId);
                if(new java.math.BigDecimal(PaypalChecks.usd(payment.amount())).signum()<=0) throw new PaymentException(400,"PayPal amount is below one USD cent");
                store.placeholder(payment.transactionID());
            }
            var binding=store.binding(orderId);
            if(binding==null) throw new PaymentException(404,"No PayPal transaction found for order "+orderId);
            if(operation.equals("CAPTURE")) {
                if(binding.gatewayId()==null || !binding.gatewayId().equals(submittedGatewayId)) throw new PaymentException(400,"PayPal order does not belong to this order");
                if(!Set.of("PENDING","SUCCESS").contains(binding.paymentStatus())) throw new PaymentException(409,"PayPal payment cannot be captured from its current status");
                if(binding.paymentStatus().equals("PENDING") && !order.status().equals("PENDING")) throw new PaymentException(409,"Order cannot accept payment from its current status");
            }
            if(operation.equals("REFUND") && (binding.captureId()==null || !Set.of("SUCCESS","REFUNDED").contains(binding.paymentStatus())))
                throw new PaymentException(400,"No successful PayPal capture found for order ID "+orderId);
            if(operation.equals("REFUND") && !order.status().equals("PENDING_PROCESSING") && !binding.paymentStatus().equals("REFUNDED"))
                throw new PaymentException(409,"Order cannot be refunded from its current status");
            store.operation(binding.transactionId(),operation);
            return new Prepared(new GatewayRequest(orderId,binding.transactionId(),binding.amount(),"PAYPAL payment for order "+orderId),binding.gatewayId(),binding.captureId());
        });
        JsonNode result=transaction(()->exchange(operation,prepared));
        return transaction(()->apply(operation,prepared,result));
    }
    private JsonNode exchange(String kind,Prepared p) {
        var operation=store.lockOperation(p.request().paymentTransactionId(),kind);JsonNode result=operation.response();
        if(result!=null) {
            if(kind.equals("CREATE") || (kind.equals("CAPTURE") && PaypalChecks.captured(result)) || (kind.equals("REFUND") && result.path("status").asText().equals("COMPLETED"))) return result;
            result=kind.equals("CAPTURE")?gateway.getOrder(p.gatewayId()):gateway.getRefund(PaypalChecks.id(result.get("id")));
        } else {
            // Conservative window below Orders' six-hour default request-id retention. Never blindly replay old money requests.
            if(operation.createdAt().isBefore(Instant.now().minus(Duration.ofHours(5)))) throw new PaymentException(409,"PayPal operation requires reconciliation before retry");
            result=switch(kind) {
                case "CREATE"->gateway.createOrder(p.request(),operation.id());
                case "CAPTURE"->gateway.captureOrder(p.gatewayId(),operation.id());
                default->gateway.refund(p.captureId(),p.request().orderId(),operation.id());
            };
        }
        store.response(operation.id(),result);return result;
    }
    private JsonNode apply(String kind,Prepared p,JsonNode result) {
        var order=store.order(p.request().orderId());
        if(order==null) throw new PaymentException(404,"Order no longer exists");
        int id=p.request().paymentTransactionId();
        var current=store.binding(p.request().orderId());
        if(current==null || current.transactionId()!=id) throw new PaymentException(409,"PayPal transaction has changed");
        if(kind.equals("CAPTURE") && current.paymentStatus().equals("REFUNDED")) throw new PaymentException(409,"PayPal payment is already refunded");
        if(kind.equals("CREATE")) {
            String gatewayId=PaypalChecks.id(result.get("id"));PaypalChecks.order(result,p.request().orderId(),p.request().amount(),gatewayId);
            String status=result.path("status").asText();if(status.isBlank() || status.length()>50) throw new PaymentException(502,"Invalid PayPal order status");
            if(current.paymentStatus().equals("PENDING")) store.created(id,gatewayId,status);
            var response=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();response.put("paypalOrderID",gatewayId).put("status",status);
            for(var link:result.path("links")) if(link.path("rel").asText().equals("approve")) {
                String url=link.path("href").asText();
                try {var uri=java.net.URI.create(url);if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || uri.getHost()==null || !(uri.getHost().equals("paypal.com") || uri.getHost().endsWith(".paypal.com"))) throw new IllegalArgumentException();}
                catch(IllegalArgumentException error) {throw new PaymentException(502,"Invalid PayPal approval URL");}
                response.put("approveUrl",url);break;
            }
            return response;
        }
        if(kind.equals("CAPTURE")) {
            var proof=gateway.verifyCapture(p.request(),p.gatewayId(),result);payments.confirm(proof);
            store.captured(id,PaypalChecks.capture(result,p.request().orderId(),p.request().amount(),p.gatewayId()));
        } else {
            PaypalChecks.id(result.get("id"));PaypalChecks.amount(result.path("amount"),p.request().amount());
            if(!result.path("status").asText().equals("COMPLETED")) throw new PaymentException(409,"PayPal refund is not completed; retry verification later");
            payments.markRefunded(new PaymentConfirmation(id,p.request().orderId(),"PAYPAL",p.request().amount()));store.refunded(id);
        }
        return result;
    }
    private void authorize(PaypalStore.OrderRow order,String token,String authorization,boolean refund) {
        if(refund) {
            if(authorization==null || !authorization.startsWith("Bearer ")) throw new PaymentException(401,"Missing manager access token");
            List<String> roles;
            try {roles=jwt.verify(authorization.substring(7)).getStringListClaim("roles");} catch(Exception error) {throw new PaymentException(401,"Invalid manager access token");}
            if(roles==null || !roles.contains("PRODUCT_MANAGER")) throw new PaymentException(403,"Bạn không có quyền truy cập chức năng này");
            if(order==null) throw new PaymentException(404,"Order not found");
        } else {
            if(token==null || token.isBlank()) throw new PaymentException(401,"Missing customer order access token");
            if(order==null || order.token()==null || !MessageDigest.isEqual(order.token().getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
                throw new PaymentException(404,"Order was not found for this access token");
        }
    }
}
