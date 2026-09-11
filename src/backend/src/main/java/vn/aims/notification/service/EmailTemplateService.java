package vn.aims.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.util.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplateService {
    public record Email(String subject,String html,String text) {}
    private final String base;
    public EmailTemplateService(@Value("${APP_PUBLIC_URL:http://localhost:4200}") String base) {
        var uri=java.net.URI.create(base);String host=uri.getHost();
        if(host==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && Set.of("localhost","127.0.0.1","[::1]").contains(host)))) throw new IllegalArgumentException("Invalid notification application URL");
        this.base=base.replaceAll("/+$","");
    }
    public Email render(JsonNode payload) {
        var order=payload.path("order");var delivery=payload.path("delivery");var invoice=payload.path("invoice");var payment=payload.path("payment");int id=order.path("order_id").asInt();
        String type=payload.path("type").asText(),title,subject,text,extra="";
        String refundStatus=value(payload,"refundStatus","No payment refund required"),refundMethod=value(payload,"refundMethod","Not applicable");
        switch(type) {
            case "ORDER_PAYMENT_SUCCEEDED"->{title="Payment confirmed";subject="AIMS Payment Confirmation - Order #"+id;text="Payment confirmed for AIMS order #"+id+". Total: "+money(order.path("total_payment"))+".";
                extra=section("Invoice details",table(new String[][]{{"Invoice ID",invoice.path("invoice_id").isMissingNode()?"N/A":"#"+invoice.path("invoice_id").asText()},{"Subtotal excluding VAT",money(fallback(invoice,"total_exclude_vat",order.path("sub_total")))},{"Total including VAT",money(invoice.path("total_include_vat").isMissingNode()?new com.fasterxml.jackson.databind.node.DecimalNode(decimal(order.path("sub_total")).add(decimal(order.path("tax")))):invoice.path("total_include_vat"))},{"Shipping fee",money(fallback(invoice,"shipping_fee",order.path("shipping_fee")))},{"Total payment",money(fallback(invoice,"total_payment",order.path("total_payment")))}}))
                    +section("Payment transaction",table(new String[][]{{"Transaction ID",payment.path("transaction_id").isMissingNode()?"N/A":"#"+payment.path("transaction_id").asText()},{"Transaction content",value(payment,"transaction_content",value(payment,"method","N/A"))},{"Transaction datetime",date(payment.path("created_at").asText())}}));}
            case "ORDER_CANCELLED"->{title="Order cancellation confirmed";subject="AIMS Order Cancellation Confirmation - Order #"+id;text="Order #"+id+" was cancelled. Refund status: "+refundStatus+".";extra=refund(order,refundMethod,refundStatus);}
            case "ORDER_APPROVED","ORDER_REJECTED"->{boolean approved=type.equals("ORDER_APPROVED");title=approved?"Your order was approved":"Your order was rejected";subject="AIMS Order "+(approved?"Approved":"Rejected")+" - Order #"+id;
                String next=approved?"Your order has been accepted and will be prepared for delivery.":"Your order could not be accepted. Refund information is shown below if payment was already completed.";text=title+" for AIMS order #"+id+". "+next;extra="<p>"+escape(next)+"</p>"+(approved?"":refund(order,refundMethod,refundStatus));}
            default->throw new IllegalArgumentException("Unsupported email type");
        }
        String summary=section("Order information",table(new String[][]{{"Order ID","#"+id},{"Customer name",value(delivery,"receiver_name","N/A")},{"Phone number",value(delivery,"phone_number","N/A")},{"Shipping address",value(delivery,"address","N/A")},{"Province",value(delivery,"province","N/A")},{"Total amount",money(order.path("total_payment"))}}));
        String token=order.path("customer_access_token").asText();String url=base+"/order-detail?orderId="+id+(token.matches("[0-9a-f]{64}")?"&token="+token:"");
        // Paid cancellation is PM-only: payment emails must not promise a customer cancellation action.
        String button="<div style=\"margin-top:28px\"><a href=\""+escape(url)+"\" style=\"display:inline-block;padding:12px 18px;background:#2563eb;color:white;text-decoration:none;border-radius:8px\">View order information</a></div>";
        String html="<div style=\"margin:0;padding:0;background:#f8fafc;font-family:Arial,Helvetica,sans-serif;color:#0f172a\"><div style=\"max-width:720px;margin:0 auto;padding:32px 16px\"><div style=\"background:white;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden\"><div style=\"padding:28px 32px;background:#0f172a;color:white\">AIMS Store<h1>"+escape(title)+"</h1></div><div style=\"padding:28px 32px\">"+summary+extra+button+"</div><div style=\"padding:18px 32px;background:#f1f5f9;color:#64748b;font-size:12px\">This is an automated notification from AIMS. If you did not place this order, please contact support.</div></div></div></div>";
        return new Email(subject,html,text);
    }
    private String refund(JsonNode order,String method,String status) {return table(new String[][]{{"Refund amount",money(order.path("total_payment"))},{"Refund method",method},{"Refund status",status}});}
    private String section(String title,String content) {return "<section style=\"margin-bottom:24px\"><h2 style=\"font-size:16px\">"+escape(title)+"</h2>"+content+"</section>";}
    private String table(String[][] rows) {var s=new StringBuilder("<table style=\"width:100%;border-collapse:collapse;border:1px solid #e2e8f0\"><tbody>");for(var row:rows)s.append("<tr><td style=\"padding:11px 14px;background:#f8fafc;color:#64748b;width:38%\">").append(escape(row[0])).append("</td><td style=\"padding:11px 14px;font-weight:600\">").append(escape(row[1])).append("</td></tr>");return s.append("</tbody></table>").toString();}
    static String escape(String s) {return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private String value(JsonNode node,String field,String fallback) {return node.path(field).isNull() || node.path(field).asText().isEmpty()?fallback:node.path(field).asText();}
    private JsonNode fallback(JsonNode node,String field,JsonNode other) {return node.path(field).isNull() || node.path(field).isMissingNode()?other:node.path(field);}
    private BigDecimal decimal(JsonNode value) {try{return new BigDecimal(value.asText());}catch(NumberFormatException e){return BigDecimal.ZERO;}}
    private String money(JsonNode value) {var f=java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));f.setMaximumFractionDigits(3);f.setRoundingMode(RoundingMode.HALF_UP);return f.format(decimal(value))+" VND";}
    private String date(String value) {try{return java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss d/M/yyyy").format(OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()));}catch(Exception e){return "N/A";}}
}
