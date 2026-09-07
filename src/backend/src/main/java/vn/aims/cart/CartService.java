package vn.aims.cart;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class CartService {
    public record StockIssue(int productId,BigInteger requestedQuantity,int availableQuantity,BigInteger shortageQuantity,String reason) {}
    public record StockCheck(boolean available,List<StockIssue> issues) {}
    public record Quote(BigDecimal subtotal,BigDecimal tax,BigDecimal shippingFee,BigDecimal totalPayment) {}
    static final class Unavailable extends RuntimeException {}
    private final CartProductRepository products;
    private final ShippingCalculator shipping;
    public CartService(CartProductRepository products,ShippingCalculator shipping) { this.products=products;this.shipping=shipping; }
    public StockCheck check(Map<Integer,BigInteger> items) {
        var found=products.find(items.keySet());var issues=new ArrayList<StockIssue>();
        items.forEach((id,quantity)-> {
            var product=found.get(id);
            if(product==null || !"ACTIVE".equals(product.status()))
                issues.add(new StockIssue(id,quantity,0,quantity,"PRODUCT_NOT_AVAILABLE"));
            else if(quantity.compareTo(BigInteger.valueOf(product.stock()))>0)
                issues.add(new StockIssue(id,quantity,product.stock(),quantity.subtract(BigInteger.valueOf(product.stock())),"INSUFFICIENT_STOCK"));
        });
        return new StockCheck(issues.isEmpty(),List.copyOf(issues));
    }
    public Quote quote(Map<Integer,BigInteger> items,String province) {
        var found=products.find(items.keySet());
        if(found.size()!=items.size()) throw new Unavailable();
        BigDecimal subtotal=BigDecimal.ZERO;double weight=0;
        for(var item:items.entrySet()) {
            var product=found.get(item.getKey());
            subtotal=subtotal.add(product.price().multiply(new BigDecimal(item.getValue())));
            weight+=product.weight()*item.getValue().doubleValue();
        }
        subtotal=subtotal.setScale(2,RoundingMode.HALF_UP);
        return totals(subtotal,shipping.fee(province,weight,subtotal,null));
    }
    static Quote totals(BigDecimal subtotal,BigDecimal fee) {
        var tax=subtotal.multiply(new BigDecimal("0.1")).setScale(2,RoundingMode.HALF_UP);
        return new Quote(subtotal,tax,fee,subtotal.add(tax).add(fee).setScale(2,RoundingMode.HALF_UP));
    }
}
