package vn.aims.order;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=OrderController.class)
public class OrderErrorHandler {
    @ExceptionHandler(OrderError.class)
    ResponseEntity<?> error(OrderError error) {
        var body=new LinkedHashMap<String,Object>();body.put("message",error.message);
        if(error.issues!=null) body.put("issues",error.issues);
        else if(error.status!=500) body.put("error",switch(error.status) {case 401->"Unauthorized";case 404->"Not Found";default->"Bad Request";});
        body.put("statusCode",error.status);return ResponseEntity.status(error.status).body(body);
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed() { return error(new OrderError(400,"Invalid JSON request body")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected() { return error(new OrderError(500,"Internal server error")); }
}
