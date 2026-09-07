package vn.aims.auth;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    public Map<String,Object> change(@RequestBody JsonNode body, Authentication auth) {
        return service.changePassword((Integer)auth.getPrincipal(),field(body,"oldPassword"),field(body,"newPassword"));
    }
}
