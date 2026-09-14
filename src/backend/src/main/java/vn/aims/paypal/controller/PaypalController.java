package vn.aims.paypal.controller;

import vn.aims.paypal.dto.PaypalInput;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.aims.paypal.service.PaypalService;

@RestController @RequestMapping("/api/paypal/order")
public class PaypalController {
    private final PaypalService service;
    public PaypalController(PaypalService service) {this.service=service;}
    @PostMapping({"/create","/create/"}) @ResponseStatus(HttpStatus.CREATED)
    public JsonNode create(@RequestBody JsonNode body,@RequestHeader(value="x-order-token",required=false) String token) {
        var input=PaypalInput.parse(body,"CREATE");return service.create(input.orderID(),token);
    }
    @PostMapping({"/capture","/capture/"}) @ResponseStatus(HttpStatus.CREATED)
    public JsonNode capture(@RequestBody JsonNode body,@RequestHeader(value="x-order-token",required=false) String token) {
        var input=PaypalInput.parse(body,"CAPTURE");return service.capture(input.orderID(),input.paypalOrderID(),token);
    }
    @PostMapping({"/refund","/refund/"}) @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_MANAGER')")
    public JsonNode refund(@RequestBody JsonNode body) {
        var input=PaypalInput.parse(body,"REFUND");return service.refund(input.orderID());
    }
}
