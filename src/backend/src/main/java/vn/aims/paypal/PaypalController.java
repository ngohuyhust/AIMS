package vn.aims.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/paypal/order")
public class PaypalController {
    private final PaypalService service;
    public PaypalController(PaypalService service) {this.service=service;}
    @PostMapping({"/create","/create/","/capture","/capture/","/refund","/refund/"}) @ResponseStatus(HttpStatus.CREATED)
    public JsonNode operation(@RequestBody JsonNode body,jakarta.servlet.http.HttpServletRequest request,
            @RequestHeader(value="x-order-token",required=false) String token,@RequestHeader(value="Authorization",required=false) String authorization) {
        String path=request.getRequestURI().replaceAll("/+$","");String operation=path.substring(path.lastIndexOf('/')+1).toUpperCase(java.util.Locale.ROOT);
        var input=PaypalInput.parse(body,operation);return service.execute(operation,input.orderID(),input.paypalOrderID(),token,authorization);
    }
}
