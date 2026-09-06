package vn.aims.product;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.Collections;
import java.util.Map;

public record ProductDetailResponse(@JsonUnwrapped ProductResponse product,
                                    @JsonAnyGetter Map<String,Object> subtype) {
    static ProductDetailResponse of(Product product, String key, Map<String,Object> detail) {
        return new ProductDetailResponse(ProductResponse.from(product), Collections.singletonMap(key,detail));
    }
}
