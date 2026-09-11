package vn.aims.order.service;

import vn.aims.order.dto.OrderResponse;
import vn.aims.order.exception.OrderError;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aims.cart.dto.CartInput;
import vn.aims.cart.service.*;
import vn.aims.order.entity.*;
import vn.aims.order.repository.OrderRepository;
import vn.aims.product.dto.ProductResponse;

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
        var order=new Order();order.setSubTotal(quote.subtotal());order.setTax(quote.tax());order.setShippingFee(quote.shippingFee());order.setTotalPayment(quote.totalPayment());
        order.setStatus("PENDING");order.setCreatedAt(Instant.now());order.setUpdatedAt(order.getCreatedAt());
        byte[] token=new byte[32];RANDOM.nextBytes(token);order.setCustomerAccessToken(HexFormat.of().formatHex(token));
        for(var item:items.entrySet()) {
            var line=new OrderItem();line.setOrder(order);line.setProduct(orders.product(item.getKey()));
            line.setQuantity(item.getValue().intValueExact());line.setUnitPrice(products.get(item.getKey()).price());order.getOrderItems().add(line);
        }
        order.setDeliveryInfo(new DeliveryInfo());order.getDeliveryInfo().setOrder(order);setDelivery(order.getDeliveryInfo(),delivery);
        order.setInvoice(new Invoice());order.getInvoice().setOrder(order);order.getInvoice().setCreatedAt(order.getCreatedAt());setInvoice(order);
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
        if(!order.getStatus().equals("PENDING"))
            throw new OrderError(400,"Order "+id+" cannot update delivery info from status "+order.getStatus());
        if(orders.hasActivePayment(id)) throw new OrderError(409,"Delivery information cannot change while payment is pending or successful");
        double weight=order.getOrderItems().stream().mapToDouble(item->item.getProduct()==null?0:ProductResponse.from(item.getProduct()).weight()*item.getQuantity()).sum();
        order.setShippingFee(shipping.fee(delivery.get("province").asText(),weight,order.getSubTotal(),null));
        order.setTotalPayment(order.getSubTotal().add(order.getTax()).add(order.getShippingFee()).setScale(2,RoundingMode.HALF_UP));
        order.setUpdatedAt(Instant.now());
        boolean newDelivery=order.getDeliveryInfo()==null;
        if(newDelivery) { order.setDeliveryInfo(new DeliveryInfo());order.getDeliveryInfo().setOrder(order); }
        setDelivery(order.getDeliveryInfo(),delivery);
        if(newDelivery) orders.persist(order.getDeliveryInfo());
        boolean newInvoice=order.getInvoice()==null;
        if(newInvoice) { order.setInvoice(new Invoice());order.getInvoice().setOrder(order);order.getInvoice().setCreatedAt(Instant.now()); }
        setInvoice(order);
        if(newInvoice) orders.persist(order.getInvoice());
        return OrderResponse.from(orders.save(order),true);
    }
    private void setDelivery(DeliveryInfo delivery,JsonNode input) {
        delivery.setReceiverName(input.get("receiverName").asText());delivery.setEmail(input.get("email").asText());
        delivery.setPhoneNumber(input.get("phoneNumber").asText());delivery.setAddress(input.get("address").asText());delivery.setProvince(input.get("province").asText());
        if(input.has("deliveryNotes")) delivery.setDeliveryNotes(input.get("deliveryNotes").isNull()?null:input.get("deliveryNotes").asText());
    }
    private void setInvoice(Order order) {
        order.getInvoice().setTotalExcludeVAT(order.getSubTotal());order.getInvoice().setTotalIncludeVAT(order.getSubTotal().add(order.getTax()).setScale(2,RoundingMode.HALF_UP));
        order.getInvoice().setShippingFee(order.getShippingFee());order.getInvoice().setTotalPayment(order.getTotalPayment());
    }
}
