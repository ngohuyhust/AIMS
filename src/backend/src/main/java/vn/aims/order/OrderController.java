package vn.aims.order;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderController {
    private final OrderService service;
    private final OrderInput input;
    public OrderController(OrderService service,OrderInput input) { this.service=service;this.input=input; }
    private int id(String value) {
        if(!value.matches("-?[0-9]+") || !Double.isFinite(Double.parseDouble(value))) throw new OrderError(400,"Validation failed (numeric string is expected)");
        try { return Integer.parseInt(value); } catch(NumberFormatException error) { throw new OrderError(500,"Internal server error"); }
    }
    @PostMapping({"/api/orders","/api/orders/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> place(@RequestBody JsonNode body) { return service.place(input.parse(body,true)); }
    @GetMapping({"/api/orders/{orderId}","/api/orders/{orderId}/"})
    public Map<String,Object> detail(@PathVariable String orderId,@RequestHeader(value="x-order-token",required=false) String token,
            @RequestHeader(value="Authorization",required=false) String authorization) {
        return service.detail(id(orderId),token,authorization,false);
    }
    @GetMapping({"/api/customer/orders/{orderId}","/api/customer/orders/{orderId}/"})
    public Map<String,Object> customer(@PathVariable String orderId,@RequestParam(value="token",required=false) String token) {
        return service.detail(id(orderId),token,null,true);
    }
    @PatchMapping({"/api/orders/{orderId}/delivery-info","/api/orders/{orderId}/delivery-info/"})
    public Map<String,Object> update(@PathVariable String orderId,@RequestHeader(value="x-order-token",required=false) String token,@RequestBody JsonNode body) {
        return service.update(id(orderId),token,input.parse(body,false));
    }
}
