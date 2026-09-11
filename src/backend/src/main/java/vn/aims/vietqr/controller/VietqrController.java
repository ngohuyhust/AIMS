package vn.aims.vietqr.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.aims.vietqr.dto.*;
import vn.aims.vietqr.service.*;

@RestController @RequestMapping("/api/vietqr/payments")
public class VietqrController {
    private final VietqrService service;
    private final VietqrMerchantTokens merchant;
    public VietqrController(VietqrService service,VietqrMerchantTokens merchant) {this.service=service;this.merchant=merchant;}
    @PostMapping({"","/"}) @ResponseStatus(HttpStatus.CREATED)
    public JsonNode create(@RequestBody JsonNode body,@RequestHeader(value="x-order-token",required=false) String token) {
        return service.create(VietqrInput.parse(body,true),token);
    }
    @GetMapping({"/{paymentId}/status","/{paymentId}/status/"})
    public JsonNode status(@PathVariable String paymentId,@RequestHeader(value="x-order-token",required=false) String token) {
        return service.status(VietqrInput.pathId(paymentId),null,token);
    }
    @GetMapping({"/by-ref/{reference}/status","/by-ref/{reference}/status/"})
    public JsonNode reference(@PathVariable String reference,@RequestHeader(value="x-order-token",required=false) String token) {
        return service.status(null,reference,token);
    }
    @PostMapping({"/{paymentId}/trigger-callback","/{paymentId}/trigger-callback/"}) @ResponseStatus(HttpStatus.CREATED)
    public Object trigger(@PathVariable String paymentId,@RequestHeader(value="Authorization",required=false) String authorization) {
        return service.trigger(VietqrInput.pathId(paymentId),authorization);
    }
    @PostMapping({"/callback","/callback/"}) @ResponseStatus(HttpStatus.CREATED)
    public Object callback(@RequestBody JsonNode body,@RequestHeader(value="Authorization",required=false) String authorization) {
        merchant.verify(authorization);return service.callback(VietqrInput.parse(body,false),authorization);
    }
}
