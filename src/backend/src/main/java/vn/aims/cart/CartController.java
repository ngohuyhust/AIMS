package vn.aims.cart;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class CartController {
    private final CartService carts;
    public CartController(CartService carts) { this.carts=carts; }
    @PostMapping({"/cart/check-stock","/cart/check-stock/"}) @ResponseStatus(HttpStatus.CREATED)
    public CartService.StockCheck check(@RequestBody JsonNode body) {
        return carts.check(CartInput.items(CartInput.parse(body,false)));
    }
    @PostMapping({"/shipping-fee","/shipping-fee/"}) @ResponseStatus(HttpStatus.CREATED)
    public CartService.Quote quote(@RequestBody JsonNode body) {
        var input=CartInput.parse(body,true);
        return carts.quote(CartInput.items(input),input.get("province").asText());
    }
}
