package vn.aims.paypal.gateway;

import vn.aims.paypal.client.PaypalApiClient;
import vn.aims.paypal.service.PaypalChecks;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import vn.aims.payment.dto.PaymentConfirmation;
import vn.aims.payment.gateway.*;

@Component
public class PaypalGateway implements CreditCardGateway {
    private final PaypalApiClient client;
    private final ObjectMapper json;
    public PaypalGateway(PaypalApiClient client,ObjectMapper json) { this.client=client;this.json=json; }
    @Override public String method() { return "PAYPAL"; }
    @Override public JsonNode createOrder(GatewayRequest request,UUID requestId) {
        var body=json.valueToTree(Map.of("intent","CAPTURE","purchase_units",List.of(Map.of("reference_id",Integer.toString(request.orderId()),
            "amount",Map.of("currency_code","USD","value",PaypalChecks.usd(request.amount())),"description","Payment for Order #"+request.orderId()+" in AIMS Store")),
            "application_context",Map.of("brand_name","AIMS Store","landing_page","NO_PREFERENCE","user_action","PAY_NOW",
                "return_url",client.frontend()+"/payment?orderId="+request.orderId()+"&success=true","cancel_url",client.frontend()+"/payment?orderId="+request.orderId()+"&cancel=true")));
        return client.call(HttpMethod.POST,"/v2/checkout/orders",requestId,body);
    }
    @Override public JsonNode captureOrder(String gatewayOrderId,UUID requestId) {
        return client.call(HttpMethod.POST,"/v2/checkout/orders/"+gatewayOrderId+"/capture",requestId,null);
    }
    @Override public JsonNode refund(String captureId,int orderId,UUID requestId) {
        return client.call(HttpMethod.POST,"/v2/payments/captures/"+captureId+"/refund",requestId,json.valueToTree(Map.of("note_to_payer","Refund for cancelled order #"+orderId)));
    }
    @Override public JsonNode getOrder(String id) { return client.call(HttpMethod.GET,"/v2/checkout/orders/"+id,null,null); }
    @Override public JsonNode getRefund(String id) { return client.call(HttpMethod.GET,"/v2/payments/refunds/"+id,null,null); }
    @Override public PaymentConfirmation verifyCapture(GatewayRequest request,String id,JsonNode raw) {
        PaypalChecks.capture(raw,request.orderId(),request.amount(),id);
        return new PaymentConfirmation(request.paymentTransactionId(),request.orderId(),method(),request.amount());
    }
}
