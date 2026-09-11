package vn.aims.user.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.Validator;

public record UserCreateInput(String email,String fullName,String phoneNumber,String password,List<String> roles) {
    record EmailValue(@Email String value) { }
    public static class Invalid extends RuntimeException {
        public final List<String> messages;
        Invalid(List<String> messages) { this.messages=List.copyOf(messages); }
    }
    public static UserCreateInput parse(JsonNode body,Validator validator) {
        var errors=new ArrayList<String>();
        String email=string(body,"email"),name=string(body,"fullName"),phone=string(body,"phoneNumber"),password=string(body,"password");
        if(!validEmail(email,validator)) errors.add("email không hợp lệ");
        for(String field:List.of("fullName","phoneNumber")) {
            String value=string(body,field);
            var raw=body.get(field);
            if(raw==null || raw.isNull() || (raw.isTextual() && value.isEmpty())) errors.add(field+" không được để trống");
            if(value==null) errors.add(field+" must be a string");
        }
        if(password==null || password.codePointCount(0,password.length())<6) errors.add("password tối thiểu 6 ký tự");
        if(password==null) errors.add("password must be a string");
        var node=body.get("roles"); var roles=new ArrayList<String>();
        if(node==null || !node.isArray()) {
            if(node==null || !node.isTextual()) errors.add("each value in roles must be a string");
            errors.add("phải chọn ít nhất một vai trò"); errors.add("roles must be an array");
        } else {
            boolean invalid=false;
            for(var value:node) { if(value.isTextual()) roles.add(value.textValue()); else invalid=true; }
            if(invalid) errors.add("each value in roles must be a string");
            if(node.isEmpty()) errors.add("phải chọn ít nhất một vai trò");
        }
        if(!errors.isEmpty()) throw new Invalid(errors);
        return new UserCreateInput(email,name,phone,password,roles);
    }
    private static String string(JsonNode body,String field) { var node=body.get(field); return node!=null && node.isTextual()?node.textValue():null; }
    private static boolean validEmail(String value,Validator validator) {
        if(value==null || value.length()>254) return false;
        int at=value.lastIndexOf('@');
        return at>0 && at<=64 && value.substring(at+1).matches("(?iu).+\\.(?:[\\p{L}]{2,}|xn--[a-z0-9-]{2,})")
                && validator.validate(new EmailValue(value)).isEmpty();
    }
}
