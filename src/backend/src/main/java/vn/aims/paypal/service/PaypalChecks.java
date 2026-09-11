package vn.aims.paypal.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import vn.aims.payment.exception.PaymentException;

public final class PaypalChecks {
    public static String usd(BigDecimal vnd) { return vnd.divide(new BigDecimal("25000"),2,RoundingMode.HALF_UP).toPlainString(); }
    public static String id(JsonNode value) {
        if(value==null || !value.isTextual() || !value.asText().matches("[A-Za-z0-9_-]{1,100}")) throw new PaymentException(502,"Invalid PayPal resource ID");return value.asText();
    }
    public static void amount(JsonNode amount,BigDecimal vnd) {
        try {
            if(!amount.path("currency_code").asText().equals("USD") || new BigDecimal(amount.path("value").asText()).compareTo(new BigDecimal(usd(vnd)))!=0) throw new IllegalArgumentException();
        } catch(RuntimeException error) { throw new PaymentException(502,"PayPal currency or amount does not match the payment"); }
    }
    public static void order(JsonNode data,int orderId,BigDecimal amount,String gatewayId) {
        if(!id(data.get("id")).equals(gatewayId) || !data.path("intent").asText().equals("CAPTURE")) throw new PaymentException(502,"PayPal order identity does not match");
        var units=data.path("purchase_units");
        if(!units.isArray() || units.size()!=1 || !units.get(0).path("reference_id").asText().equals(Integer.toString(orderId))) throw new PaymentException(502,"PayPal order reference does not match");
        amount(units.get(0).path("amount"),amount);
    }
    public static boolean captured(JsonNode data) {
        var captures=data.path("purchase_units").path(0).path("payments").path("captures");
        return data.path("status").asText().equals("COMPLETED") && captures.isArray() && captures.size()==1 && captures.get(0).path("status").asText().equals("COMPLETED");
    }
    public static String capture(JsonNode data,int orderId,BigDecimal amount,String gatewayId) {
        order(data,orderId,amount,gatewayId);
        if(!captured(data)) throw new PaymentException(409,"PayPal payment is not completed; retry verification later");
        var capture=data.path("purchase_units").get(0).path("payments").path("captures").get(0);amount(capture.path("amount"),amount);
        return id(capture.get("id"));
    }
}
