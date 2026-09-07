package vn.aims.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CartInputTest {
    @Test void matchesOriginalValidationPipe() throws Exception {
        var json=new ObjectMapper();
        try(var stream=getClass().getResourceAsStream("/cart/validation.json")) {
            for(var fixture:json.readTree(stream)) {
                if(fixture.has("errors")) {
                    var error=catchThrowableOfType(()->CartInput.parse(fixture.get("input"),fixture.get("shipping").asBoolean()),CartInput.Invalid.class);
                    assertThat(error).as(fixture.toString()).isNotNull();
                    com.fasterxml.jackson.databind.JsonNode messages=json.valueToTree(error.messages);
                    assertThat(messages).as(fixture.toString()).isEqualTo(fixture.get("errors"));
                } else assertThat(CartInput.parse(fixture.get("input"),fixture.get("shipping").asBoolean())).isEqualTo(fixture.get("expected"));
            }
        }
    }
    @Test void mergePreservesOrderAndDoesNotOverflowInt() throws Exception {
        var items=CartInput.items(new ObjectMapper().readTree("""
            {"cartItems":[{"productId":2,"quantity":2147483647},{"productId":1,"quantity":1},{"productId":2,"quantity":2}]}
            """));
        assertThat(items.keySet()).containsExactly(2,1);
        assertThat(items.get(2)).isEqualTo(new java.math.BigInteger("2147483649"));
    }
}
