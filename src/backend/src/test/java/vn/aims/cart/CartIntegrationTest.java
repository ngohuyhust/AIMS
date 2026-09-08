package vn.aims.cart;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc
class CartIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM product_logs");jdbc.update("DELETE FROM products");
        for(int id=1;id<=5;id++) jdbc.update("""
            INSERT INTO products(product_id,product_type,title,category,barcode,weight,original_value,current_price,quantity_in_stock,status,length,width,height)
            VALUES (?,'BOOK','Test','Book',?,0.5,50000,50000,?,?,50,60,10)
            """,id,"cart-"+id,id==5?0:3,switch(id) {case 2->"INACTIVE";case 3->"DEACTIVATED";case 4->"DELETED";default->"ACTIVE";});
    }
    JsonNode postJson(String path,String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/orders/"+path).header("Authorization","Bearer stale")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString());
    }
    @Test void stockMergesDuplicatesAndPreservesIssueOrderWithoutWrites() throws Exception {
        var before=jdbc.queryForList("SELECT * FROM products ORDER BY product_id");
        var result=postJson("cart/check-stock","""
            {"cartItems":[{"productId":1,"quantity":2},{"productId":99,"quantity":1},{"productId":1,"quantity":2},
              {"productId":2,"quantity":1},{"productId":3,"quantity":1},{"productId":4,"quantity":1},{"productId":5,"quantity":1}]}
            """);
        assertThat(result.path("available").asBoolean()).isFalse();
        assertThat(result.path("issues").size()).isEqualTo(6);
        assertThat(result.path("issues").get(0)).isEqualTo(json.readTree("""
            {"productId":1,"requestedQuantity":4,"availableQuantity":3,"shortageQuantity":1,"reason":"INSUFFICIENT_STOCK"}
            """));
        int[] order={1,99,2,3,4,5};
        for(int i=1;i<5;i++) {
            var issue=result.path("issues").get(i);
            assertThat(issue.path("productId").asInt()).isEqualTo(order[i]);
            assertThat(issue.path("reason").asText()).isEqualTo("PRODUCT_NOT_AVAILABLE");
            assertThat(issue.path("availableQuantity").asInt()).isZero();
        }
        assertThat(result.path("issues").get(5).path("reason").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(jdbc.queryForList("SELECT * FROM products ORDER BY product_id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_logs",Integer.class)).isZero();
    }
    @Test void exactStockAndLargeMergedQuantity() throws Exception {
        assertThat(postJson("cart/check-stock/","{\"cartItems\":[{\"productId\":1,\"quantity\":3}]}")).isEqualTo(json.readTree("{\"available\":true,\"issues\":[]}"));
        var result=postJson("cart/check-stock","{\"cartItems\":[{\"productId\":1,\"quantity\":2147483647},{\"productId\":1,\"quantity\":2}]}");
        assertThat(result.path("issues").get(0).path("requestedQuantity").asLong()).isEqualTo(2147483649L);
        assertThat(result.path("issues").get(0).path("shortageQuantity").asLong()).isEqualTo(2147483646L);
    }
    @Test void quoteUsesCurrentPricesDuplicatesAndWeightOnlyWithStrictDiscountThreshold() throws Exception {
        var result=postJson("shipping-fee","{\"province\":\"Hà Nội\",\"cartItems\":[{\"productId\":1,\"quantity\":1},{\"productId\":1,\"quantity\":1}]}");
        assertThat(result).isEqualTo(json.readTree("{\"subtotal\":100000.0,\"tax\":10000.0,\"shippingFee\":22000,\"totalPayment\":132000.0}"));
        var discounted=postJson("shipping-fee/","{\"province\":\"TP.HCM\",\"cartItems\":[{\"productId\":1,\"quantity\":3}]}");
        assertThat(discounted.path("shippingFee").decimalValue()).isEqualByComparingTo("0");
        assertThat(discounted.path("totalPayment").decimalValue()).isEqualByComparingTo("165000");
        assertThat(discounted.path("subtotal").isNumber()).isTrue();
    }
    @Test void quotePreservesLegacyStatusAndStockBehaviorButMissingProductFails() throws Exception {
        for(int id=2;id<=5;id++) {
            var result=postJson("shipping-fee","{\"province\":\"Đà Nẵng\",\"cartItems\":[{\"productId\":"+id+",\"quantity\":4}]}");
            assertThat(result.path("subtotal").decimalValue()).isEqualByComparingTo("200000");
            assertThat(result.path("shippingFee").decimalValue()).isEqualByComparingTo("12500");
        }
        mvc.perform(post("/api/orders/shipping-fee").contentType(MediaType.APPLICATION_JSON).content("{\"province\":\"\",\"cartItems\":[{\"productId\":99,\"quantity\":1}]}"))
            .andExpect(status().isBadRequest()).andExpect(content().json("{\"statusCode\":400,\"message\":\"Some products are not available\",\"error\":\"Bad Request\"}",true));
    }
    @Test void quoteRoundsVatAndNeverTrustsClientPricesOrDimensions() throws Exception {
        jdbc.update("UPDATE products SET original_value=0.15,current_price=0.15 WHERE product_id=1");
        var result=postJson("shipping-fee","{\"province\":\"hn\",\"subtotal\":0,\"cartItems\":[{\"productId\":1,\"quantity\":1,\"currentPrice\":0,\"weight\":1000}]}");
        assertThat(result.path("subtotal").decimalValue()).isEqualByComparingTo("0.15");
        assertThat(result.path("tax").decimalValue()).isEqualByComparingTo("0.02");
        assertThat(result.path("totalPayment").decimalValue()).isEqualByComparingTo("22000.17");
        // Explicitly approved decimal correction: legacy JS rounds this VAT to 0.03.
        jdbc.update("UPDATE products SET original_value=0.35,current_price=0.35 WHERE product_id=1");
        var corrected=postJson("shipping-fee","{\"province\":\"hn\",\"cartItems\":[{\"productId\":1,\"quantity\":1}]}");
        assertThat(corrected.path("tax").decimalValue()).isEqualByComparingTo("0.04");
        assertThat(corrected.path("totalPayment").decimalValue()).isEqualByComparingTo("22000.39");
    }
    @Test void validationEnvelopesMatchOriginalAndMalformedJsonIsBadRequest() throws Exception {
        try(var stream=getClass().getResourceAsStream("/cart/validation.json")) {
            for(var fixture:json.readTree(stream)) if(fixture.has("errors")) {
                var response=mvc.perform(post("/api/orders/"+(fixture.path("shipping").asBoolean()?"shipping-fee":"cart/check-stock"))
                    .contentType(MediaType.APPLICATION_JSON).content(fixture.get("input").toString())).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400)).andExpect(jsonPath("$.error").value("Bad Request"))
                    .andReturn().getResponse().getContentAsString();
                assertThat(json.readTree(response).get("message")).isEqualTo(fixture.get("errors"));
            }
        }
        mvc.perform(post("/api/orders/shipping-fee").contentType(MediaType.APPLICATION_JSON).content("{" )).andExpect(status().isBadRequest());
    }
    @Test void publicCorsWorksAndFutureRoutesRemainClosed() throws Exception {
        mvc.perform(post("/api/orders/cart/check-stock").header("Origin","http://localhost:4200")
            .contentType(MediaType.APPLICATION_JSON).content("{\"cartItems\":[{\"productId\":1,\"quantity\":1}]}"))
            .andExpect(status().isCreated()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:4200"));
        mvc.perform(options("/api/orders/shipping-fee").header("Origin","http://localhost:4200")
            .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type"))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/orders/1/approve").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/pending")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/orders/1/status")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('carts')",Integer.class)).isZero();
    }
}
