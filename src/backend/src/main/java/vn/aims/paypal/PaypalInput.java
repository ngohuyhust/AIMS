package vn.aims.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import vn.aims.payment.PaymentException;

final class PaypalInput {
    record Input(int orderID,String paypalOrderID) {}
    static final class Invalid extends RuntimeException {
        final List<String> messages;
        Invalid(List<String> messages) { this.messages=List.copyOf(messages); }
    }
    static Input parse(JsonNode body,String operation) {
        var errors=new ArrayList<String>();boolean capture=operation.equals("CAPTURE"),refund=operation.equals("REFUND");
        var gateway=body.get("paypalOrderID");
        if(capture) {
            if(gateway==null || !gateway.isTextual()) errors.add("paypalOrderID phải là một chuỗi ký tự");
            if(gateway==null || gateway.isNull() || (gateway.isTextual() && gateway.asText().isEmpty())) errors.add("Mã paypalOrderID từ PayPal không được để trống");
        }
        var id=body.get("orderID");boolean number=id!=null && id.isNumber() && Double.isFinite(id.asDouble());
        if(!number || id.asDouble()<=0) errors.add(capture?"orderID phải là số dương":refund?"Invalid orderID":"orderID must be a positive number");
        if(!number) errors.add(capture?"orderID phải là một số":refund?"Invalid orderID":"orderID must be a number");
        if(id==null || id.isNull() || (id.isTextual() && id.asText().isEmpty())) errors.add(capture?"orderID hệ thống không được để trống":refund?"Mising orderID":"Missing orderID");
        if(!errors.isEmpty()) throw new Invalid(errors);
        if(!id.canConvertToInt() || id.decimalValue().stripTrailingZeros().scale()>0) throw new PaymentException(500,"Internal server error");
        return new Input(id.intValue(),capture?gateway.asText():null);
    }
}
