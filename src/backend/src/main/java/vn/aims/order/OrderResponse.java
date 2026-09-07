package vn.aims.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import vn.aims.product.ProductResponse;

/** Explicit acyclic TypeORM-shaped graph; persisted decimal columns remain JSON strings. */
final class OrderResponse {
    static String money(BigDecimal value) { return value.setScale(2).toPlainString(); }
    static String date(Instant value) { return new DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(value); }
    static Map<String,Object> from(Order order,boolean exposeToken) {
        var result=new LinkedHashMap<String,Object>();
        result.put("orderID",order.orderID);result.put("subTotal",money(order.subTotal));result.put("tax",money(order.tax));
        result.put("shippingFee",money(order.shippingFee));result.put("totalPayment",money(order.totalPayment));result.put("status",order.status);
        if(exposeToken) result.put("customerAccessToken",order.customerAccessToken);
        result.put("createdAt",date(order.createdAt));result.put("updatedAt",date(order.updatedAt));
        result.put("orderItems",order.orderItems.stream().map(item->{
            var row=new LinkedHashMap<String,Object>();row.put("orderItemID",item.orderItemID);row.put("quantity",item.quantity);
            row.put("unitPrice",money(item.unitPrice));row.put("product",item.product==null?null:ProductResponse.from(item.product));return row;
        }).toList());
        var delivery=order.deliveryInfo;
        if(delivery==null) result.put("deliveryInfo",null);
        else {
            var row=new LinkedHashMap<String,Object>();row.put("deliveryID",delivery.deliveryID);row.put("receiverName",delivery.receiverName);
            row.put("email",delivery.email);row.put("phoneNumber",delivery.phoneNumber);row.put("address",delivery.address);
            row.put("province",delivery.province);row.put("deliveryNotes",delivery.deliveryNotes);result.put("deliveryInfo",row);
        }
        var invoice=order.invoice;
        if(invoice==null) result.put("invoice",null);
        else result.put("invoice",Map.of("invoiceID",invoice.invoiceID,"totalExcludeVAT",money(invoice.totalExcludeVAT),
            "totalIncludeVAT",money(invoice.totalIncludeVAT),"shippingFee",money(invoice.shippingFee),"totalPayment",money(invoice.totalPayment),"createdAt",date(invoice.createdAt)));
        return result;
    }
}
