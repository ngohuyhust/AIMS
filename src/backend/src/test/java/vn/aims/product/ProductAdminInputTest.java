package vn.aims.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import vn.aims.product.dto.ProductAdminInput;

class ProductAdminInputTest {
    @Test void dtoValidationMatchesOriginalNestPipeFixtures() throws Exception {
        var json=new ObjectMapper();
        try(var input=getClass().getResourceAsStream("/product-admin/validation.json")) {
            for(var fixture:json.readTree(input)) {
                String name=fixture.get("name").asText();
                if(fixture.has("errors")) {
                    var exception=catchThrowableOfType(()->ProductAdminInput.parse(fixture.get("input"),fixture.get("update").asBoolean()),ProductAdminInput.Invalid.class);
                    assertThat(exception).as(name).isNotNull();
                    com.fasterxml.jackson.databind.JsonNode messages=json.valueToTree(exception.messages);
                    assertThat(messages).as(name).isEqualTo(fixture.get("errors"));
                } else assertThat(ProductAdminInput.parse(fixture.get("input"),fixture.get("update").asBoolean())).as(name).isEqualTo(fixture.get("expected"));
            }
        }
    }
}
