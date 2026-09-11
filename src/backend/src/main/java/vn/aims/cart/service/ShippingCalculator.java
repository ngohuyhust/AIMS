package vn.aims.cart.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import vn.aims.cart.service.ShippingStrategy;

@Service
public class ShippingCalculator {
    private static final Set<String> INNER=Set.of("ha noi","hanoi","hn","tp hcm","tp ho chi minh","tphcm","ho chi minh","ho chi minh city","hcm");
    private static final BigDecimal THRESHOLD=new BigDecimal("100000"),DISCOUNT=new BigDecimal("25000"),EXTRA=new BigDecimal("2500");
    private final ShippingStrategy strategy;
    public ShippingCalculator(ShippingStrategy strategy) { this.strategy=strategy; }
    public BigDecimal fee(String province,double totalWeight,BigDecimal subtotal,ShippingStrategy.Dimensions dimensions) {
        // Explicit ECMAScript whitespace set, including BOM and excluding U+0085.
        String normalized=Normalizer.normalize(province,Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]","")
                .toLowerCase(Locale.ROOT).replaceAll("[.\\x09-\\x0d\\x20\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+"," ").trim();
        boolean inner=INNER.contains(normalized);
        double weight=Math.max(strategy.chargeableWeight(Math.max(totalWeight,0),dimensions),0);
        double baseWeight=inner?3:0.5;
        var fee=BigDecimal.valueOf(inner?22000:30000);
        if(weight>baseWeight) fee=fee.add(EXTRA.multiply(BigDecimal.valueOf(Math.ceil((weight-baseWeight)/0.5))));
        return subtotal.compareTo(THRESHOLD)>0?fee.subtract(DISCOUNT).max(BigDecimal.ZERO):fee;
    }
}
