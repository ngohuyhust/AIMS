package vn.aims.user;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.aims.auth.AuthError;

@RestController
@PreAuthorize("hasAuthority('ADMIN')")
public class UserAdminController {
    private final UserAdminService service;
    private final Validator validator;
    public UserAdminController(UserAdminService service,Validator validator) { this.service=service; this.validator=validator; }
    private String actor(Authentication auth) { return (String)auth.getDetails(); }
    private int id(String value) {
        if(!value.matches("-?[0-9]+")) throw new AuthError(400,"Validation failed (numeric string is expected)");
        try { return Integer.parseInt(value); } catch(NumberFormatException error) { throw new AuthError(400,"Validation failed (numeric string is expected)"); }
    }
    @PostMapping({"/api/users","/api/users/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@RequestBody JsonNode body,Authentication auth) { return service.create(UserCreateInput.parse(body,validator),actor(auth)); }
    @GetMapping({"/api/users","/api/users/"})
    public List<Map<String,Object>> list() { return service.list(); }
    @GetMapping({"/api/users/logs","/api/users/logs/"})
    public List<Map<String,Object>> logs() { return service.allLogs(); }
    @PatchMapping({"/api/users/{userId}","/api/users/{userId}/"})
    public Map<String,Object> update(@PathVariable String userId,@RequestBody JsonNode body,Authentication auth) { return service.update(id(userId),body,actor(auth)); }
    @PatchMapping({"/api/users/{userId}/status","/api/users/{userId}/status/"})
    public Map<String,Object> status(@PathVariable String userId,@RequestBody JsonNode body,Authentication auth) { return service.status(id(userId),UserAdminService.text(body.get("status")),actor(auth)); }
    @PatchMapping({"/api/users/{userId}/roles","/api/users/{userId}/roles/"})
    public Map<String,Object> roles(@PathVariable String userId,@RequestBody JsonNode body,Authentication auth) {
        int parsed=id(userId); var node=body.get("roles");
        if(node==null || !node.isArray() || node.isEmpty()) throw new AuthError(400,"Danh sách vai trò không được trống");
        var names=new ArrayList<String>();
        node.forEach(value -> { if(!value.isTextual()) throw new AuthError(400,"Vai trò không hợp lệ"); names.add(value.textValue()); });
        return service.roles(parsed,names,actor(auth));
    }
    @PostMapping({"/api/users/{userId}/reset-password","/api/users/{userId}/reset-password/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> reset(@PathVariable String userId,Authentication auth) { return service.reset(id(userId),actor(auth),false); }
    @PostMapping({"/api/auth/reset-password/{userId}","/api/auth/reset-password/{userId}/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> authReset(@PathVariable String userId,Authentication auth) { return service.reset(id(userId),actor(auth),true); }
}
