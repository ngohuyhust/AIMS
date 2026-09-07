package vn.aims.order;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.aims.cart.CartInput;

@Component
public class OrderInput {
    record EmailValue(@Email String value) {}
    private final Validator validator;
    public OrderInput(Validator validator) { this.validator=validator; }
    ObjectNode parse(JsonNode body,boolean placement) {
        var errors=new ArrayList<String>();var clean=JsonNodeFactory.instance.objectNode();
        if(placement) {
            try { clean=CartInput.parse(body,false); } catch(CartInput.Invalid error) { errors.addAll(error.messages); }
            if(body.has("deliveryInfo")) {
                var delivery=body.get("deliveryInfo");
                if(!delivery.isObject()) errors.add("nested property deliveryInfo must be either object or array");
                else clean.set("deliveryInfo",delivery(delivery,"deliveryInfo.",errors));
            }
        } else clean=delivery(body,"",errors);
        if(!errors.isEmpty()) throw new OrderError(400,errors);
        return clean;
    }
    private ObjectNode delivery(JsonNode body,String prefix,List<String> errors) {
        var clean=JsonNodeFactory.instance.objectNode();
        for(String field:List.of("receiverName","email","phoneNumber","address","province","deliveryNotes")) {
            var node=body.get(field);String value=node!=null && node.isTextual()?node.asText():null;
            if(node!=null) clean.set(field,node);
            if(field.equals("deliveryNotes") && (node==null || node.isNull())) continue;
            int max=switch(field) {case "phoneNumber"->20;case "province"->100;default->255;};
            // Retains the original character class (including literal '|') and anchored phone match.
            if(field.equals("phoneNumber") && (value==null || !value.matches("(?:0|\\+84)[3|5|7|8|9][0-9]{8}"))) errors.add(prefix+"Số điện thoại không đúng định dạng");
            if(!field.equals("deliveryNotes") && (value==null || value.codePointCount(0,value.length())>max))
                errors.add(prefix+field+" must be shorter than or equal to "+max+" characters");
            if(field.equals("email")) {
                if(!email(value)) errors.add(prefix+"Email không đúng định dạng");
            } else if(value==null) errors.add(prefix+field+" must be a string");
        }
        return clean;
    }
    private boolean email(String value) {
        if(value==null || value.length()>254) return false;
        int at=value.lastIndexOf('@');
        return at>0 && at<=64 && value.substring(at+1).matches("(?iu).+\\.(?:[\\p{L}]{2,}|xn--[a-z0-9-]{2,})")
                && validator.validate(new EmailValue(value)).isEmpty();
    }
}
