package vn.aims.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ShippingCalculatorTest {
    @TestFactory Stream<DynamicTest> originalShippingBoundaries() throws Exception {
        var json=new ObjectMapper();
        try(var stream=getClass().getResourceAsStream("/cart/shipping.json")) {
            var cases=json.readTree(stream);
            return java.util.stream.StreamSupport.stream(cases.spliterator(),false).map(f->DynamicTest.dynamicTest(f.toString(),()-> {
                var calculator=new ShippingCalculator(f.path("volumetric").asBoolean()?new VolumetricShippingStrategy():new WeightOnlyShippingStrategy());
                ShippingStrategy.Dimensions dimensions=f.has("dimensions")?json.treeToValue(f.get("dimensions"),ShippingStrategy.Dimensions.class):null;
                assertThat(calculator.fee(f.get("province").asText(),f.get("weight").asDouble(),f.get("subtotal").decimalValue(),dimensions))
                    .isEqualByComparingTo(f.get("expected").decimalValue());
            }));
        }
    }
    @Test void vatRoundsHalfCentsUpAndTotalUsesRoundedTax() {
        String[][] cases={{"0.04","0.00"},{"0.05","0.01"},{"0.06","0.01"},{"0.15","0.02"},
                {"0.35","0.04"},{"1.15","0.12"},{"10.15","1.02"},{"100000.01","10000.00"}};
        for(var row:cases) {
            var quote=CartService.totals(new BigDecimal(row[0]),new BigDecimal("22000"));
            assertThat(quote.tax()).isEqualByComparingTo(row[1]);
            assertThat(quote.totalPayment()).isEqualByComparingTo(quote.subtotal().add(quote.tax()).add(quote.shippingFee()));
        }
    }
}
