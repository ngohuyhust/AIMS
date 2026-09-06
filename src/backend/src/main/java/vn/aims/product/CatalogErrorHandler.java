package vn.aims.product;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Scoped to migrated catalog routes; preserves the Nest JSON envelope. */
@RestControllerAdvice(assignableTypes = ProductController.class)
public class CatalogErrorHandler {
    @ExceptionHandler(CatalogException.class)
    ResponseEntity<Map<String,Object>> catalog(CatalogException exception) {
        if (exception.status == 500) return unexpected();
        return ResponseEntity.status(exception.status).body(Map.of("statusCode",exception.status,
                "message",exception.getMessage(),"error",exception.status == 404 ? "Not Found" : "Bad Request"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,Object>> unexpected() {
        return ResponseEntity.internalServerError().body(Map.of("statusCode",500,"message","Internal server error"));
    }
}
