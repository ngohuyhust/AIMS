package vn.aims.product.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.aims.product.dto.*;
import vn.aims.product.exception.*;
import vn.aims.product.service.*;

@RestController @RequestMapping("/api/products") @PreAuthorize("hasAuthority('PRODUCT_MANAGER')")
public class ProductAdminController {
    private final ProductAdminService admin;
    private final ProductService catalog;
    public ProductAdminController(ProductAdminService admin,ProductService catalog) { this.admin=admin;this.catalog=catalog; }
    private String actor(String header,Jwt jwt) {
        if(header==null || ProductSearch.trim(header).isEmpty()) throw new CatalogException(400,"x-manager-id header is required");
        // Explicitly approved: retain header contract but derive trusted audit/quota identity from JWT.
        String email=jwt.getClaimAsString("email");
        if(email==null || email.isBlank()) throw new CatalogException(401,"Authenticated manager email is required");
        return email;
    }
    private int id(String input) {
        if(!input.matches("-?[0-9]+")) throw new CatalogException(400,"Validation failed (numeric string is expected)");
        try { return Integer.parseInt(input); } catch(NumberFormatException error) { throw new CatalogException(500,"Internal server error"); }
    }
    @PostMapping({"","/"}) @ResponseStatus(HttpStatus.CREATED)
    public ProductDetailResponse create(@RequestBody JsonNode body,@RequestHeader(value="x-manager-id",required=false) String header,@AuthenticationPrincipal Jwt jwt) {
        var dto=ProductAdminInput.parse(body,false);String actor=actor(header,jwt);return catalog.detail(admin.create(dto,actor));
    }
    @PatchMapping({"/{id}","/{id}/"})
    public ProductDetailResponse update(@PathVariable String id,@RequestBody JsonNode body,@RequestHeader(value="x-manager-id",required=false) String header,@AuthenticationPrincipal Jwt jwt) {
        int key=id(id);var dto=ProductAdminInput.parse(body,true);String actor=actor(header,jwt);return catalog.detail(admin.update(key,dto,actor));
    }
    @PatchMapping({"/{id}/stock","/{id}/stock/"})
    public ProductDetailResponse stock(@PathVariable String id,@RequestBody JsonNode body,@RequestHeader(value="x-manager-id",required=false) String header,@AuthenticationPrincipal Jwt jwt) {
        int key=id(id),delta=ProductAdminInput.delta(body);return catalog.detail(admin.stock(key,delta,body.get("reason").asText(),actor(header,jwt)));
    }
    @PostMapping({"/batch-delete","/batch-delete/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> delete(@RequestBody JsonNode body,@RequestHeader(value="x-manager-id",required=false) String header,@AuthenticationPrincipal Jwt jwt) {
        var ids=ProductAdminInput.ids(body);return admin.batch(ids,false,actor(header,jwt));
    }
    @PostMapping({"/batch-deactivate","/batch-deactivate/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> deactivate(@RequestBody JsonNode body,@RequestHeader(value="x-manager-id",required=false) String header,@AuthenticationPrincipal Jwt jwt) {
        var ids=ProductAdminInput.ids(body);return admin.batch(ids,true,actor(header,jwt));
    }
    @GetMapping({"/audit-logs","/audit-logs/"})
    public List<Map<String,Object>> logs(@RequestHeader(value="x-manager-id",required=false) String header,@AuthenticationPrincipal Jwt jwt) { actor(header,jwt);return admin.logs(); }
}
