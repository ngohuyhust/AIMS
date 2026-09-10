package vn.aims.order.api;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.aims.order.application.OrderError;

@RestControllerAdvice(assignableTypes={OrderController.class,OrderManagementController.class})
public class OrderErrorHandler {
    @ExceptionHandler(OrderError.class)
    ResponseEntity<?> error(OrderError error) {
        var body=new LinkedHashMap<String,Object>();body.put("message",error.responseMessage());
        if(error.issues()!=null) body.put("issues",error.issues());
        else if(error.status()!=500) body.put("error",switch(error.status()) {case 401->"Unauthorized";case 403->"Forbidden";case 502->"Bad Gateway";case 503->"Service Unavailable";case 404->"Not Found";case 409->"Conflict";default->"Bad Request";});
        body.put("statusCode",error.status());return ResponseEntity.status(error.status()).body(body);
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed() { return error(new OrderError(400,"Invalid JSON request body")); }
    @ExceptionHandler(vn.aims.payment.PaymentException.class)
    ResponseEntity<?> payment(vn.aims.payment.PaymentException e) { return error(new OrderError(e.status(),e.getMessage())); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected() { return error(new OrderError(500,"Internal server error")); }
}
