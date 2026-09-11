package vn.aims.vietqr.controller;

import vn.aims.vietqr.dto.VietqrInput;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.aims.payment.exception.PaymentException;

@RestControllerAdvice(assignableTypes={VietqrController.class,VietqrMerchantController.class})
public class VietqrErrorHandler {
    private ResponseEntity<?> error(int status,Object message) {
        var body=new LinkedHashMap<String,Object>();body.put("statusCode",status);body.put("message",message);
        if(status!=500) body.put("error",switch(status) {case 401->"Unauthorized";case 403->"Forbidden";case 404->"Not Found";case 409->"Conflict";case 502->"Bad Gateway";case 503->"Service Unavailable";default->"Bad Request";});
        return ResponseEntity.status(status).body(body);
    }
    @ExceptionHandler(VietqrInput.Invalid.class) ResponseEntity<?> invalid(VietqrInput.Invalid e) {return error(400,e.messages);}
    @ExceptionHandler(PaymentException.class) ResponseEntity<?> domain(PaymentException e) {return error(e.status(),e.getMessage());}
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) ResponseEntity<?> malformed() {return error(400,"Invalid JSON request body");}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected() {return error(500,"Internal server error");}
}
