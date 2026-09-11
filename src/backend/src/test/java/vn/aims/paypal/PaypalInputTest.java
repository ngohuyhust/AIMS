package vn.aims.paypal;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import vn.aims.paypal.dto.PaypalInput;
import vn.aims.paypal.client.*;
import vn.aims.paypal.gateway.*;
import vn.aims.paypal.repository.*;
import vn.aims.paypal.service.*;
import vn.aims.payment.exception.PaymentException;

class PaypalInputTest {
    @Test void matchesOriginalDtoFixturesIncludingErrorOrderAndWhitelist() throws Exception {
        var json=new ObjectMapper();
        try(var stream=getClass().getResourceAsStream("/paypal/validation.json")) {
            for(var f:json.readTree(stream)) {
                if(f.has("errors")) {
                    var error=catchThrowableOfType(()->PaypalInput.parse(f.get("input"),f.path("operation").asText()),PaypalInput.Invalid.class);
                    assertThat(error).as(f.toString()).isNotNull();
                    assertThat((JsonNode)json.valueToTree(error.messages)).as(f.toString()).isEqualTo(f.get("errors"));
                } else if(f.path("input").path("orderID").asDouble()==1.25) {
                    assertThatThrownBy(()->PaypalInput.parse(f.get("input"),f.path("operation").asText())).isInstanceOf(PaymentException.class);
                } else {
                    var input=PaypalInput.parse(f.get("input"),f.path("operation").asText());
                    assertThat(input.orderID()).isEqualTo(f.path("expected").path("orderID").asInt());
                    if(f.path("operation").asText().equals("CAPTURE")) assertThat(input.paypalOrderID()).isEqualTo(f.path("expected").path("paypalOrderID").asText());
                }
            }
        }
    }
    @Test void usesApprovedDecimalHalfUpAndRejectsUntrustedPaymentProof() throws Exception {
        assertThat(PaypalChecks.usd(new BigDecimal("125"))).isEqualTo("0.01");
        var json=new ObjectMapper();
        for(String currency:java.util.List.of("EUR","VND","")) assertThatThrownBy(()->PaypalChecks.amount(json.readTree("{\"currency_code\":\""+currency+"\",\"value\":\"5.28\"}"),new BigDecimal("132000"))).isInstanceOf(PaymentException.class);
        for(String id:java.util.List.of("../capture","", "a/b", "x?other")) assertThatThrownBy(()->PaypalChecks.id(json.valueToTree(id))).isInstanceOf(PaymentException.class);
        var proof=json.readTree("{\"id\":\"P1\",\"intent\":\"CAPTURE\",\"purchase_units\":[{\"reference_id\":\"2\",\"amount\":{\"currency_code\":\"USD\",\"value\":\"5.28\"}}]}");
        assertThatThrownBy(()->PaypalChecks.order(proof,1,new BigDecimal("132000"),"P1")).isInstanceOf(PaymentException.class);
    }
    @Test void refusesInsecureRemoteTransportAndMissingCredentials() {
        assertThatThrownBy(()->new PaypalApiClient("http://example.test","","","http://localhost:4200")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PaypalApiClient("https://api-m.sandbox.paypal.com","","","http://localhost:4200").configured()).isInstanceOf(PaymentException.class);
    }
}
