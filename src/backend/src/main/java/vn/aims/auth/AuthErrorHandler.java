package vn.aims.auth;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {AuthController.class,vn.aims.user.UserAdminController.class})
public class AuthErrorHandler {
    public static Map<String,Object> body(int status, String message) {
        return Map.of("statusCode",status,"message",message,"error",switch (status) {
            case 401 -> "Unauthorized"; case 403 -> "Forbidden"; case 404 -> "Not Found"; default -> "Bad Request";
        });
    }
    @ExceptionHandler(AuthError.class)
    ResponseEntity<?> handle(AuthError error) { return ResponseEntity.status(error.status).body(body(error.status,error.getMessage())); }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed() { return ResponseEntity.badRequest().body(body(400,"Invalid JSON request")); }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<?> databaseFailure() {
        return ResponseEntity.internalServerError().body(Map.of("statusCode",500,"message","Internal server error"));
    }
    @ExceptionHandler(vn.aims.user.UserCreateInput.Invalid.class)
    ResponseEntity<?> validation(vn.aims.user.UserCreateInput.Invalid error) {
        return ResponseEntity.badRequest().body(Map.of("statusCode",400,"error","Bad Request","message",error.messages));
    }
}
