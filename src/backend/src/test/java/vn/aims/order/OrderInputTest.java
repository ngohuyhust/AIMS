package vn.aims.order;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import vn.aims.order.dto.OrderInput;
import vn.aims.order.exception.OrderError;

class OrderInputTest {
    @Test void originalDtoFixtures() throws Exception {
        var json=new ObjectMapper();
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory();var stream=getClass().getResourceAsStream("/order/validation.json")) {
            var input=new OrderInput(factory.getValidator());
            for(var fixture:json.readTree(stream)) {
                if(fixture.has("errors")) {
                    var error=catchThrowableOfType(()->input.parse(fixture.get("input"),fixture.get("placement").asBoolean()),OrderError.class);
                    assertThat(error).as(fixture.toString()).isNotNull();
                    JsonNode messages=json.valueToTree(error.responseMessage());
                    assertThat(messages).as(fixture.toString()).isEqualTo(fixture.get("errors"));
                } else assertThat(input.parse(fixture.get("input"),fixture.get("placement").asBoolean())).isEqualTo(fixture.get("expected"));
            }
        }
    }
}
