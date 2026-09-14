package vn.aims.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.aims.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service=service; }
    private String field(JsonNode body, String name) {
        var value = body.get(name);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
    @PostMapping({"/login","/login/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> login(@RequestBody JsonNode body) {
        return service.login(field(body,"email"),field(body,"password"));
    }
    @PostMapping({"/change-password","/change-password/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> change(@RequestBody JsonNode body, @AuthenticationPrincipal Jwt jwt) {
        return service.changePassword(((Number)jwt.getClaim("userID")).intValue(),field(body,"oldPassword"),field(body,"newPassword"));
    }
}
