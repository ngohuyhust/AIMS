package vn.aims.product;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Scoped to migrated catalog routes; preserves the Nest JSON envelope. */
@RestControllerAdvice(assignableTypes = {ProductController.class,ProductAdminController.class})
public class CatalogErrorHandler {
    @ExceptionHandler(CatalogException.class)
    ResponseEntity<Map<String,Object>> catalog(CatalogException exception) {
        if (exception.status == 500) return unexpected();
        return ResponseEntity.status(exception.status).body(Map.of("statusCode",exception.status,
                "message",exception.getMessage(),"error",exception.status == 404 ? "Not Found" : exception.status == 401 ? "Unauthorized" : "Bad Request"));
    }
    @ExceptionHandler(ProductAdminInput.Invalid.class)
    ResponseEntity<Map<String,Object>> invalid(ProductAdminInput.Invalid error) {
        return ResponseEntity.badRequest().body(Map.of("statusCode",400,"message",error.messages,"error","Bad Request"));
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Map<String,Object>> denied() {
        return ResponseEntity.status(403).body(Map.of("statusCode",403,"message","Bạn không có quyền truy cập chức năng này","error","Forbidden"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,Object>> unexpected() {
        return ResponseEntity.internalServerError().body(Map.of("statusCode",500,"message","Internal server error"));
    }
}
