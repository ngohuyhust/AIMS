package vn.aims.vietqr.dto;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.*;
import java.util.*;
import vn.aims.payment.exception.PaymentException;

public final class VietqrInput {
    public static final class Invalid extends RuntimeException {
        public final List<String> messages;
        Invalid(List<String> messages) {this.messages=List.copyOf(messages);}
    }
    public static ObjectNode parse(JsonNode body,boolean create) {
        var clean=JsonNodeFactory.instance.objectNode();var errors=new ArrayList<String>();
        var fields=create?List.of("orderId","amount","content"):List.of("bankaccount","amount","transType","content","transactionid","transactiontime","referencenumber","orderId","terminalCode","sign");
        for(String key:fields) {
            JsonNode value=body.get(key);
            if(create && (key.equals("orderId") || key.equals("amount"))) value=number(value);
            if(value!=null) clean.set(key,value);
            boolean optional=key.equals("terminalCode") || key.equals("sign");if(optional && (value==null || value.isNull())) continue;
            boolean numeric=key.equals("amount") || key.equals("transactiontime") || (create && key.equals("orderId"));
            boolean validNumber=value!=null && value.isNumber() && Double.isFinite(value.asDouble());
            if(numeric) {
                if(!key.equals("transactiontime") && (!validNumber || value.asDouble()<=0)) errors.add(key+" must be positive");
                if(create && key.equals("orderId")) {if(!validNumber || value.decimalValue().stripTrailingZeros().scale()>0) errors.add("orderId must be an integer");}
                else if(!validNumber) errors.add(key+" must be a number");
            } else {
                if(create && key.equals("content")) {
                    if(value==null || !value.isTextual() || !value.asText().matches("[A-Za-z0-9 ]+")) errors.add("content must contain only non-accented letters, numbers, and spaces");
                    if(value==null || !value.isTextual() || value.asText().isEmpty() || value.asText().codePointCount(0,value.asText().length())>23) errors.add("content must be between 1 and 23 characters");
                }
                if(key.equals("transType") && (value==null || !value.isTextual() || !Set.of("C","D").contains(value.asText()))) errors.add("transType must be C or D");
                if(value==null || !value.isTextual()) errors.add(key+" must be a string");
            }
            if(!optional && (value==null || value.isNull() || (value.isTextual() && value.asText().isEmpty()))) errors.add(key+" is required");
        }
        if(!errors.isEmpty()) throw new Invalid(errors);return clean;
    }
    private static JsonNode number(JsonNode v) {
        if(v==null || v.isNull() || v.isNumber() || v.isContainerNode()) return v;
        if(v.isBoolean()) return IntNode.valueOf(v.asBoolean()?1:0);
        try {
            String s=v.asText().strip();
            if(s.isEmpty()) return IntNode.valueOf(0);
            if(s.matches("0[xX][0-9a-fA-F]+")) return new BigIntegerNode(new BigInteger(s.substring(2),16));
            if(s.matches("0[bB][01]+")) return new BigIntegerNode(new BigInteger(s.substring(2),2));
            if(s.matches("0[oO][0-7]+")) return new BigIntegerNode(new BigInteger(s.substring(2),8));
            return DecimalNode.valueOf(new BigDecimal(s));
        } catch(NumberFormatException e) {return v;}
    }
    public static int id(JsonNode value) {
        if(value==null || !value.canConvertToInt() || value.asInt()<=0) throw new PaymentException(400,"Invalid payment or order ID");return value.asInt();
    }
    public static int pathId(String value) {
        if(!value.matches("-?[0-9]+")) throw new PaymentException(400,"Validation failed (numeric string is expected)");
        try {return Integer.parseInt(value);} catch(NumberFormatException e) {throw new PaymentException(500,"Internal server error");}
    }
}
