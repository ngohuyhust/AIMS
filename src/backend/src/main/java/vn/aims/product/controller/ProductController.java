package vn.aims.product.controller;

import java.math.BigInteger;
import java.util.List;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import vn.aims.product.dto.*;
import vn.aims.product.exception.*;
import vn.aims.product.service.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }

    @GetMapping({"", "/"})
    public List<ProductResponse> search(@RequestParam MultiValueMap<String,String> query) {
        return service.search(ProductSearch.parse(single(query,"keyword"),single(query,"category"),
                single(query,"mediaTypes"),single(query,"minPrice"),single(query,"maxPrice"),single(query,"status")));
    }
    @GetMapping({"/random", "/random/"})
    public List<ProductResponse> random() { return service.random(); }

    @GetMapping({"/{id}", "/{id}/"})
    public ProductDetailResponse detail(@PathVariable String id) {
        if (!id.matches("-?[0-9]+") || !Double.isFinite(Double.parseDouble(id))) {
            throw new CatalogException(400,"Validation failed (numeric string is expected)");
        }
        BigInteger number = new BigInteger(id);
        if (number.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || number.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            // Nest ParseIntPipe accepts it, but the PostgreSQL integer lookup rejects it.
            throw new CatalogException(500,"Internal server error");
        }
        return service.detail(number.intValue());
    }

    private String single(MultiValueMap<String,String> query, String name) {
        var values=query.get(name);
        if (values == null) return null;
        if (values.size() > 1) {
            if (name.equals("minPrice") || name.equals("maxPrice")) throw new CatalogException(400,name+" must be a number");
            throw new CatalogException(500,"Internal server error");
        }
        return values.getFirst();
    }
}
