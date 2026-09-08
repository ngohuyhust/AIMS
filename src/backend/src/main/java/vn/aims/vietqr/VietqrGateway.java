package vn.aims.vietqr;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import vn.aims.payment.*;
import vn.aims.payment.gateway.*;

/** Caller owns the transaction and applies shared-core results before committing. */
@Component @Transactional(propagation=Propagation.MANDATORY)
public class VietqrGateway implements QrCodeGateway {
    private final VietqrStore store;
    private final VietqrApiClient client;
    private final ObjectMapper json;
    private final long ttlMillis;
    public VietqrGateway(VietqrStore store,VietqrApiClient client,ObjectMapper json,@Value("${VIETQR_PAYMENT_TTL_MINUTES:15}") String ttl) {
        this.store=store;this.client=client;this.json=json;
        double minutes;try{minutes=Double.parseDouble(ttl);}catch(NumberFormatException e){minutes=15;}
        ttlMillis=Double.isFinite(minutes) && minutes>0 && minutes<=525600?Math.max(1,(long)(minutes*60000)):900000;
    }
    @Override public String method() {return "VIETQR";}
    @Override public Optional<JsonNode> findReusablePending(int order,BigDecimal amount) {
        store.lockOrder(order);
        return store.pending(order).stream().filter(r->r.amount().compareTo(amount)==0 && r.expires().isAfter(Instant.now()) && (r.qrCode()!=null || r.qrLink()!=null)).findFirst().map(this::response);
    }
    void prepare(GatewayRequest request) {
        store.lockOrder(request.orderId());client.configured();
        String content=normalize(request.content());
        store.placeholder(request.paymentTransactionId(),request.orderId(),request.amount(),content,Instant.now().plusMillis(ttlMillis));
    }
    @Override public JsonNode createPayment(GatewayRequest request) {
        store.lockOrder(request.orderId());var row=required(request.paymentTransactionId());
        if(!row.status().equals("PENDING") || !row.expires().isAfter(Instant.now())) throw new PaymentException(409,"VietQR payment cannot generate a QR from its current status");
        if(row.qrCode()==null && row.qrLink()==null) store.generated(row.id(),client.generate(row.orderId(),row.amount(),row.content()));
        return response(required(row.paymentId()));
    }
    @Override public StatusResult getStatusByPaymentId(int id) {
        var row=required(id);store.lockOrder(row.orderId());row=required(id);
        boolean expired=row.status().equals("PENDING") && !row.expires().isAfter(Instant.now());
        if(expired) {store.expire(row);row=required(id);}return new StatusResult(response(row),expired,id);
    }
    @Override public StatusResult getStatusByTransactionRef(String ref) {
        var rows=store.byReference(ref);if(rows.isEmpty()) throw new PaymentException(404,"VietQR transaction reference "+ref+" was not found");
        if(rows.size()!=1) throw new PaymentException(409,"Ambiguous VietQR transaction reference");return getStatusByPaymentId(rows.getFirst().paymentId());
    }
    @Override public CallbackResult handleCallback(JsonNode dto) {
        if(!dto.path("transType").asText().equals("C")) throw new PaymentException(400,"Only credit VietQR callbacks can mark a payment as paid");
        if(client.bankAccount().isBlank() || !client.bankAccount().equals(dto.path("bankaccount").asText())) throw new PaymentException(400,"VietQR callback bank account does not match");
        String orderText=dto.path("orderId").asText(),ref=dto.path("referencenumber").asText().trim(),bankId=dto.path("transactionid").asText();
        int order;try{order=Integer.parseInt(orderText);}catch(NumberFormatException e){throw new PaymentException(400,"Invalid VietQR callback orderId");}
        if(order<=0 || !Integer.toString(order).equals(orderText) || ref.isEmpty() || ref.length()>100 || bankId.isBlank() || bankId.length()>100) throw new PaymentException(400,"Invalid VietQR callback identity");
        store.lockOrder(order);
        var matches=store.byReference(ref);
        if(matches.isEmpty()) matches=store.byDetails(order,normalize(dto.path("content").asText()),dto.path("amount").decimalValue());
        if(matches.isEmpty()) throw new PaymentException(404,"Matching VietQR payment was not found");
        if(matches.size()!=1) throw new PaymentException(409,"Ambiguous VietQR callback requires reconciliation");
        var row=matches.getFirst();
        if(row.orderId()!=order || row.amount().compareTo(dto.path("amount").decimalValue())!=0 || !row.content().equals(normalize(dto.path("content").asText()))) throw new PaymentException(400,"VietQR callback order, amount or content does not match payment");
        long time=dto.path("transactiontime").asLong();
        if(time<=0 || time>Instant.now().plusSeconds(300).toEpochMilli() || time<row.created().minusSeconds(300).toEpochMilli()) throw new PaymentException(400,"Invalid VietQR callback transaction time");
        if(!Set.of("PENDING","PAID").contains(row.status())) throw new PaymentException(409,"VietQR payment is "+row.status());
        if(row.status().equals("PENDING") && !row.expires().isAfter(Instant.now())) throw new PaymentException(409,"VietQR payment is expired; reconciliation required");
        store.receipt(client.bankAccount(),bankId,row.paymentId());boolean changed=row.status().equals("PENDING");
        if(changed) store.paid(row,dto);
        return new CallbackResult(changed,new PaymentConfirmation(row.paymentId(),row.orderId(),method(),row.amount()),changed?"Callback processed":"Callback already processed");
    }
    // A sandbox provider may call us synchronously; never hold an order lock while triggering it.
    @Transactional(propagation=Propagation.NEVER)
    public void trigger(int payment) {var row=required(payment);if(!row.status().equals("PENDING") || !row.expires().isAfter(Instant.now())) throw new PaymentException(400,"Payment is not pending or has expired");client.trigger(row.content(),row.amount());}
    VietqrStore.Row required(int id) {var row=store.byPayment(id);if(row==null) throw new PaymentException(404,"VietQR payment "+id+" was not found");return row;}
    static String normalize(String content) {
        String value=content.trim().toUpperCase(Locale.ROOT);
        if(value.length()>23 || !value.matches("[A-Z0-9 ]+")) throw new PaymentException(400,"VietQR payment content must be at most 23 non-accented alphanumeric characters");return value;
    }
    JsonNode response(VietqrStore.Row row) {
        var node=json.createObjectNode();node.put("paymentId",row.paymentId()).put("orderId",row.orderId()).put("amount",row.amount());
        node.put("transactionRef",row.reference()).put("content",row.content()).put("paymentContent",row.content()).put("qrCode",row.qrCode()).put("qrLink",row.qrLink());
        node.put("expiredAt",new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(row.expires())).put("status",row.status());
        node.put("bankCode",client.bankCode()).put("bankAccount",client.bankAccount()).put("bankAccountName",client.bankName());return node;
    }
}
