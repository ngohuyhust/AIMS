package vn.aims.order;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aims.cart.*;
import vn.aims.product.ProductResponse;

@Service
public class OrderService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final OrderRepository orders;
    private final ShippingCalculator shipping;
    private final OrderAccess access;
    public OrderService(OrderRepository orders,ShippingCalculator shipping,OrderAccess access) { this.orders=orders;this.shipping=shipping;this.access=access; }
    @Transactional
    public Map<String,Object> place(JsonNode input) {
        var items=CartInput.items(input);var products=orders.lockProducts(items.keySet());
        var issues=new ArrayList<CartService.StockIssue>();
        items.forEach((id,quantity)-> {
            var product=products.get(id);
            if(product==null || !"ACTIVE".equals(product.status())) issues.add(new CartService.StockIssue(id,quantity,0,quantity,"PRODUCT_NOT_AVAILABLE"));
            else if(quantity.compareTo(BigInteger.valueOf(product.quantity()))>0)
                issues.add(new CartService.StockIssue(id,quantity,product.quantity(),quantity.subtract(BigInteger.valueOf(product.quantity())),"INSUFFICIENT_STOCK"));
        });
        if(!issues.isEmpty()) throw new OrderError(400,"Some products do not have enough stock",issues);
        BigDecimal subtotal=BigDecimal.ZERO;double weight=0;
        for(var item:items.entrySet()) {
            var product=products.get(item.getKey());int quantity=item.getValue().intValueExact();
            orders.reserve(item.getKey(),quantity);
            subtotal=subtotal.add(product.price().multiply(BigDecimal.valueOf(quantity)));weight+=product.weight()*quantity;
        }
        var delivery=input.get("deliveryInfo");
        // Legacy DTO omits IsDefined; missing delivery fails inside the transaction and rolls back.
        var quote=CartService.totals(subtotal,shipping.fee(delivery.get("province").asText(),weight,subtotal,null));
        var order=new Order();order.subTotal=quote.subtotal();order.tax=quote.tax();order.shippingFee=quote.shippingFee();order.totalPayment=quote.totalPayment();
        order.status="PENDING";order.createdAt=order.updatedAt=Instant.now();
        byte[] token=new byte[32];RANDOM.nextBytes(token);order.customerAccessToken=HexFormat.of().formatHex(token);
        for(var item:items.entrySet()) {
            var line=new OrderItem();line.order=order;line.product=orders.product(item.getKey());
            line.quantity=item.getValue().intValueExact();line.unitPrice=products.get(item.getKey()).price();order.orderItems.add(line);
        }
        order.deliveryInfo=new DeliveryInfo();order.deliveryInfo.order=order;setDelivery(order.deliveryInfo,delivery);
        order.invoice=new Invoice();order.invoice.order=order;order.invoice.createdAt=order.createdAt;setInvoice(order);
        return OrderResponse.from(orders.save(order),true);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(int id,String token,String authorization,boolean customerRoute) {
        if(customerRoute && (token==null || token.isBlank())) throw new OrderError(400,"Missing customer order access token");
        boolean manager=!customerRoute && access.manager(authorization);
        var order=orders.find(id,false);access.require(order,id,token,manager);
        var response=OrderResponse.from(order,!manager);response.put("paymentMethod",orders.paymentMethod(id));return response;
    }
    @Transactional
    public Map<String,Object> update(int id,String token,JsonNode delivery) {
        var order=orders.find(id,true);access.require(order,id,token,false);
        if(!order.status.equals("PENDING"))
            throw new OrderError(400,"Order "+id+" cannot update delivery info from status "+order.status);
        if(orders.hasActivePayment(id)) throw new OrderError(409,"Delivery information cannot change while payment is pending or successful");
        double weight=order.orderItems.stream().mapToDouble(item->item.product==null?0:ProductResponse.from(item.product).weight()*item.quantity).sum();
        order.shippingFee=shipping.fee(delivery.get("province").asText(),weight,order.subTotal,null);
        order.totalPayment=order.subTotal.add(order.tax).add(order.shippingFee).setScale(2,RoundingMode.HALF_UP);
        order.updatedAt=Instant.now();
        boolean newDelivery=order.deliveryInfo==null;
        if(newDelivery) { order.deliveryInfo=new DeliveryInfo();order.deliveryInfo.order=order; }
        setDelivery(order.deliveryInfo,delivery);
        if(newDelivery) orders.persist(order.deliveryInfo);
        boolean newInvoice=order.invoice==null;
        if(newInvoice) { order.invoice=new Invoice();order.invoice.order=order;order.invoice.createdAt=Instant.now(); }
        setInvoice(order);
        if(newInvoice) orders.persist(order.invoice);
        return OrderResponse.from(orders.save(order),true);
    }
    private void setDelivery(DeliveryInfo delivery,JsonNode input) {
        delivery.receiverName=input.get("receiverName").asText();delivery.email=input.get("email").asText();
        delivery.phoneNumber=input.get("phoneNumber").asText();delivery.address=input.get("address").asText();delivery.province=input.get("province").asText();
        if(input.has("deliveryNotes")) delivery.deliveryNotes=input.get("deliveryNotes").isNull()?null:input.get("deliveryNotes").asText();
    }
    private void setInvoice(Order order) {
        order.invoice.totalExcludeVAT=order.subTotal;order.invoice.totalIncludeVAT=order.subTotal.add(order.tax).setScale(2,RoundingMode.HALF_UP);
        order.invoice.shippingFee=order.shippingFee;order.invoice.totalPayment=order.totalPayment;
    }
}
