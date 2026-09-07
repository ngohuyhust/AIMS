package vn.aims.product;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/products") @PreAuthorize("hasAuthority('PRODUCT_MANAGER')")
public class ProductAdminController {
    private final ProductAdminService admin;
    private final ProductService catalog;
    public ProductAdminController(ProductAdminService admin,ProductService catalog) { this.admin=admin;this.catalog=catalog; }
    private String actor(String header,Authentication auth) {
        if(header==null || ProductSearch.trim(header).isEmpty()) throw new CatalogException(400,"x-manager-id header is required");
        // Explicitly approved: retain header contract but derive trusted audit/quota identity from JWT.
        if(!(auth.getDetails() instanceof String email) || email.isBlank()) throw new CatalogException(401,"Authenticated manager email is required");
        return (String)auth.getDetails();
    }
    private int id(String input) {
        if(!input.matches("-?[0-9]+")) throw new CatalogException(400,"Validation failed (numeric string is expected)");
        try { return Integer.parseInt(input); } catch(NumberFormatException error) { throw new CatalogException(500,"Internal server error"); }
    }
    @PostMapping({"","/"}) @ResponseStatus(HttpStatus.CREATED)
    public ProductDetailResponse create(@RequestBody JsonNode body,@RequestHeader(value="x-manager-id",required=false) String header,Authentication auth) {
        var dto=ProductAdminInput.parse(body,false);String actor=actor(header,auth);return catalog.detail(admin.create(dto,actor));
    }
    @PatchMapping({"/{id}","/{id}/"})
    public ProductDetailResponse update(@PathVariable String id,@RequestBody JsonNode body,@RequestHeader(value="x-manager-id",required=false) String header,Authentication auth) {
        int key=id(id);var dto=ProductAdminInput.parse(body,true);String actor=actor(header,auth);return catalog.detail(admin.update(key,dto,actor));
    }
    @PatchMapping({"/{id}/stock","/{id}/stock/"})
    public ProductDetailResponse stock(@PathVariable String id,@RequestBody JsonNode body,@RequestHeader(value="x-manager-id",required=false) String header,Authentication auth) {
        int key=id(id),delta=ProductAdminInput.delta(body);return catalog.detail(admin.stock(key,delta,body.get("reason").asText(),actor(header,auth)));
    }
    @PostMapping({"/batch-delete","/batch-delete/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> delete(@RequestBody JsonNode body,@RequestHeader(value="x-manager-id",required=false) String header,Authentication auth) {
        var ids=ProductAdminInput.ids(body);return admin.batch(ids,false,actor(header,auth));
    }
    @PostMapping({"/batch-deactivate","/batch-deactivate/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> deactivate(@RequestBody JsonNode body,@RequestHeader(value="x-manager-id",required=false) String header,Authentication auth) {
        var ids=ProductAdminInput.ids(body);return admin.batch(ids,true,actor(header,auth));
    }
    @GetMapping({"/audit-logs","/audit-logs/"})
    public List<Map<String,Object>> logs(@RequestHeader(value="x-manager-id",required=false) String header,Authentication auth) { actor(header,auth);return admin.logs(); }
}
