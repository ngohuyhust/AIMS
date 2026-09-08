package vn.aims.vietqr;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VietqrInputTest {
    @Test void original98DtoFixtures() throws Exception {
        var json=new ObjectMapper();
        try(var stream=getClass().getResourceAsStream("/vietqr/validation.json")) {
            for(var f:json.readTree(stream)) {
                if(f.has("errors")) {
                    var e=catchThrowableOfType(()->VietqrInput.parse(f.get("input"),f.path("kind").asText().equals("CREATE")),VietqrInput.Invalid.class);
                    assertThat(e).as(f.toString()).isNotNull();
                    assertThat((JsonNode)json.valueToTree(e.messages)).as(f.toString()).isEqualTo(f.get("errors"));
                } else assertThat(json.readTree(VietqrInput.parse(f.get("input"),f.path("kind").asText().equals("CREATE")).toString())).as(f.toString()).isEqualTo(f.get("expected"));
            }
        }
    }
}
