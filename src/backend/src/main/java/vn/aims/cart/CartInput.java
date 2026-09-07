package vn.aims.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.*;

/** Preserve the original whitelist and class-validator message order, without string coercion. */
public final class CartInput {
    public static final class Invalid extends RuntimeException {
        public final List<String> messages;
        Invalid(List<String> messages) { this.messages=List.copyOf(new LinkedHashSet<>(messages)); }
    }
    public static ObjectNode parse(JsonNode body,boolean shipping) {
        var errors=new ArrayList<String>();
        var clean=JsonNodeFactory.instance.objectNode();
        if(shipping) {
            var province=body.get("province");
            if(province==null || !province.isTextual() || province.asText().codePointCount(0,province.asText().length())>100)
                errors.add("province must be shorter than or equal to 100 characters");
            if(province==null || !province.isTextual()) errors.add("province must be a string");
            if(province!=null) clean.set("province",province);
        }
        var items=body.get("cartItems");
        var nested=new ArrayList<String>();
        if(items!=null) clean.set("cartItems",nested(items,"cartItems",nested));
        // Nest flattens children instead of their parent constraints when nested fields fail.
        if(nested.isEmpty() || items==null || (!items.isArray() && !items.isObject())) {
            if(items==null || !items.isArray() || items.isEmpty()) errors.add("cartItems must contain at least 1 elements");
        }
        errors.addAll(nested);
        if(!errors.isEmpty()) throw new Invalid(errors);
        return clean;
    }
    private static JsonNode nested(JsonNode item,String prefix,List<String> errors) {
        if(item.isArray()) {
            var clean=JsonNodeFactory.instance.arrayNode();int index=0;
            for(var child:item) {
                if(!child.isContainerNode()) {
                    errors.add(prefix+".each value in nested property cartItems must be either object or array");
                    clean.add(child);
                } else clean.add(nested(child,prefix+"."+index,errors));
                index++;
            }
            return clean;
        }
        if(!item.isObject()) {
            errors.add("each value in nested property cartItems must be either object or array");return item;
        }
        var clean=JsonNodeFactory.instance.objectNode();
        for(String field:List.of("productId","quantity")) {
            var value=item.get(field);
            boolean number=value!=null && value.isNumber() && Double.isFinite(value.doubleValue());
            if(!number || value.doubleValue()<1) errors.add(prefix+"."+field+" must not be less than 1");
            if(!number || value.decimalValue().stripTrailingZeros().scale()>0) errors.add(prefix+"."+field+" must be an integer number");
            if(value!=null) clean.set(field,value);
        }
        return clean;
    }
    public static LinkedHashMap<Integer,BigInteger> items(JsonNode body) {
        var result=new LinkedHashMap<Integer,BigInteger>();
        if(!body.path("cartItems").isArray()) throw new IllegalArgumentException("Cart is not iterable");
        for(var item:body.get("cartItems")) {
            var id=item.get("productId");
            if(id==null || !id.canConvertToInt()) throw new IllegalArgumentException("Product ID outside PostgreSQL integer range");
            result.merge(id.intValue(),item.get("quantity").decimalValue().toBigIntegerExact(),BigInteger::add);
        }
        return result;
    }
}
