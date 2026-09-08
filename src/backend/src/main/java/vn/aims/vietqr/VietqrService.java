package vn.aims.vietqr;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import vn.aims.auth.JwtTokens;
import vn.aims.payment.*;
import vn.aims.payment.gateway.GatewayRequest;

@Service @Transactional(propagation=Propagation.NEVER)
public class VietqrService {
    private final VietqrStore store;private final VietqrGateway gateway;private final PaymentService payments;
    private final VietqrMerchantTokens merchant;private final JwtTokens jwt;private final boolean testEnabled;private final TransactionTemplate tx;
    public VietqrService(VietqrStore store,VietqrGateway gateway,PaymentService payments,VietqrMerchantTokens merchant,JwtTokens jwt,
        @Value("${VIETQR_ENABLE_TEST_CALLBACK:false}") boolean testEnabled,PlatformTransactionManager manager) {
        this.store=store;this.gateway=gateway;this.payments=payments;this.merchant=merchant;this.jwt=jwt;this.testEnabled=testEnabled;tx=new TransactionTemplate(manager);
    }
    private <T> T transaction(Supplier<T> action) {return tx.execute(s->action.get());}
    public JsonNode create(JsonNode dto,String token) {
        var request=transaction(()->{
            int order=VietqrInput.id(dto.get("orderId"));authorize(order,token);
            for(var row:store.pending(order)) {var result=gateway.getStatusByPaymentId(row.paymentId());if(result.expired()) payments.fail(row.paymentId());}
            var p=payments.begin(order,"VIETQR",PaymentService.wholeVnd(dto.path("amount").decimalValue()),VietqrGateway.normalize(dto.path("content").asText()));
            var r=new GatewayRequest(order,p.transactionID(),p.amount(),dto.path("content").asText());gateway.prepare(r);return r;
        });
        return transaction(()->{authorize(request.orderId(),token);return gateway.createPayment(request);});
    }
    public JsonNode status(Integer paymentId,String reference,String token) {
        missingToken(token);
        return transaction(()->{
            int id;
            if(paymentId!=null) id=paymentId;
            else {var rows=store.byReference(reference);if(rows.size()!=1) throw new PaymentException(rows.isEmpty()?404:409,"VietQR reference was not found or is ambiguous");id=rows.getFirst().paymentId();}
            var row=gateway.required(id);authorize(row.orderId(),token);
            var result=gateway.getStatusByPaymentId(id);if(result.expired()) payments.fail(id);return result.response();
        });
    }
    public Map<String,Object> callback(JsonNode dto,String authorization) {
        merchant.verify(authorization);
        return transaction(()->{
            var result=gateway.handleCallback(dto);if(result.changed()) payments.confirm(result.confirmation());
            return Map.of("status","SUCCESS","message",result.message(),"paymentId",result.confirmation().paymentTransactionId());
        });
    }
    public Map<String,String> trigger(int id,String authorization) {
        if(!testEnabled) throw new PaymentException(403,"VietQR test callback is disabled");
        try {
            if(authorization==null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
            if(!jwt.verify(authorization.substring(7)).getStringListClaim("roles").contains("PRODUCT_MANAGER")) throw new PaymentException(403,"Product manager role is required");
        } catch(PaymentException e) {throw e;} catch(Exception e) {throw new PaymentException(401,"Invalid manager access token");}
        gateway.trigger(id);return Map.of("status","SUCCESS");
    }
    private void missingToken(String token) {if(token==null || token.isBlank()) throw new PaymentException(401,"Missing customer order access token");}
    private void authorize(int order,String token) {
        missingToken(token);String expected=store.lockOrder(order);
        if(expected==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8))) throw new PaymentException(404,"Order was not found for this access token");
    }
}
