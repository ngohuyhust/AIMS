package vn.aims.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import vn.aims.order.entity.Order;
import vn.aims.product.dto.ProductResponse;

/** Explicit acyclic TypeORM-shaped graph; persisted decimal columns remain JSON strings. */
public final class OrderResponse {
    public static String money(BigDecimal value) { return value.setScale(2).toPlainString(); }
    public static String date(Instant value) { return new DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(value); }
    public static Map<String,Object> from(Order order,boolean exposeToken) {
        var result=new LinkedHashMap<String,Object>();
        result.put("orderID",order.getOrderID());result.put("subTotal",money(order.getSubTotal()));result.put("tax",money(order.getTax()));
        result.put("shippingFee",money(order.getShippingFee()));result.put("totalPayment",money(order.getTotalPayment()));result.put("status",order.getStatus());
        if(exposeToken) result.put("customerAccessToken",order.getCustomerAccessToken());
        result.put("createdAt",date(order.getCreatedAt()));result.put("updatedAt",date(order.getUpdatedAt()));
        result.put("orderItems",order.getOrderItems().stream().map(item->{
            var row=new LinkedHashMap<String,Object>();row.put("orderItemID",item.getOrderItemID());row.put("quantity",item.getQuantity());
            row.put("unitPrice",money(item.getUnitPrice()));row.put("product",item.getProduct()==null?null:ProductResponse.from(item.getProduct()));return row;
        }).toList());
        var delivery=order.getDeliveryInfo();
        if(delivery==null) result.put("deliveryInfo",null);
        else {
            var row=new LinkedHashMap<String,Object>();row.put("deliveryID",delivery.getDeliveryID());row.put("receiverName",delivery.getReceiverName());
            row.put("email",delivery.getEmail());row.put("phoneNumber",delivery.getPhoneNumber());row.put("address",delivery.getAddress());
            row.put("province",delivery.getProvince());row.put("deliveryNotes",delivery.getDeliveryNotes());result.put("deliveryInfo",row);
        }
        var invoice=order.getInvoice();
        if(invoice==null) result.put("invoice",null);
        else result.put("invoice",Map.of("invoiceID",invoice.getInvoiceID(),"totalExcludeVAT",money(invoice.getTotalExcludeVAT()),
            "totalIncludeVAT",money(invoice.getTotalIncludeVAT()),"shippingFee",money(invoice.getShippingFee()),"totalPayment",money(invoice.getTotalPayment()),"createdAt",date(invoice.getCreatedAt())));
        return result;
    }
}
