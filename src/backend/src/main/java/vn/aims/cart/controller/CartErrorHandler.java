package vn.aims.cart.controller;

import vn.aims.cart.dto.CartInput;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.aims.cart.service.CartService;

@RestControllerAdvice(assignableTypes=CartController.class)
public class CartErrorHandler {
    @ExceptionHandler(CartInput.Invalid.class)
    ResponseEntity<?> invalid(CartInput.Invalid error) { return badRequest(error.messages); }
    @ExceptionHandler(CartService.Unavailable.class)
    ResponseEntity<?> unavailable() { return badRequest("Some products are not available"); }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed() { return badRequest("Invalid JSON request body"); }
    private ResponseEntity<?> badRequest(Object message) {
        return ResponseEntity.badRequest().body(Map.of("statusCode",400,"message",message,"error","Bad Request"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected() {
        return ResponseEntity.internalServerError().body(Map.of("statusCode",500,"message","Internal server error"));
    }
}
