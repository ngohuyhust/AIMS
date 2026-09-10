package vn.aims.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.aims.order.application.*;

@RestController
public class OrderManagementController {
    private final OrderLifecycleService lifecycle;private final OrderQueryService query;
    public OrderManagementController(OrderLifecycleService lifecycle,OrderQueryService query) {this.lifecycle=lifecycle;this.query=query;}
    private int number(String value,int fallback) {
        if(value==null)return fallback;if(!value.matches("-?[0-9]+"))throw new OrderError(400,"Validation failed (numeric string is expected)");
        try{return Integer.parseInt(value);}catch(NumberFormatException e){throw new OrderError(500,"Internal server error");}
    }
    @GetMapping({"/api/orders/pending","/api/orders/pending/","/api/orders/vietqr-refunds","/api/orders/vietqr-refunds/"})
    public Object list(jakarta.servlet.http.HttpServletRequest request,@RequestParam(required=false) String page,@RequestParam(required=false) String limit,@RequestParam(required=false) String search,@RequestParam(required=false) String dateRange,@RequestParam(required=false) String paymentMethod) {
        return query.list(request.getRequestURI().contains("vietqr-refunds"),number(page,1),number(limit,30),search,dateRange,paymentMethod);
    }
    @PostMapping({"/api/orders/{orderId}/approve","/api/orders/{orderId}/approve/"}) @ResponseStatus(HttpStatus.CREATED)
    public Object approve(@PathVariable String orderId,@RequestHeader("Authorization") String authorization) {return lifecycle.approve(number(orderId,0),authorization);}
    @PostMapping({"/api/orders/{orderId}/cancel","/api/orders/{orderId}/cancel/","/api/orders/{orderId}/reject","/api/orders/{orderId}/reject/"}) @ResponseStatus(HttpStatus.CREATED)
    public Object close(@PathVariable String orderId,@RequestHeader("Authorization") String authorization,jakarta.servlet.http.HttpServletRequest request) {return lifecycle.close(number(orderId,0),request.getRequestURI().matches(".*/reject/?")?"REJECT":"CANCEL",null,authorization,false);}
    @PostMapping({"/api/orders/{orderId}/confirm-vietqr-refund","/api/orders/{orderId}/confirm-vietqr-refund/"}) @ResponseStatus(HttpStatus.CREATED)
    public Object refund(@PathVariable String orderId,@RequestHeader("Authorization") String authorization) {return lifecycle.confirmVietqrRefund(number(orderId,0),authorization);}
    @PostMapping({"/api/customer/orders/{orderId}/cancel","/api/customer/orders/{orderId}/cancel/"}) @ResponseStatus(HttpStatus.CREATED)
    public Object customer(@PathVariable String orderId,@RequestParam(required=false) String token) {return lifecycle.close(number(orderId,0),"CANCEL",token,null,true);}
}
