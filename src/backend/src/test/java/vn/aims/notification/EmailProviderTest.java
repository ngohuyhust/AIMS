package vn.aims.notification;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sendgrid.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import vn.aims.notification.event.*;
import vn.aims.notification.provider.*;
import vn.aims.notification.service.*;
import vn.aims.notification.provider.SendGridEmailProvider;

class EmailProviderTest {
    final ObjectMapper json=new ObjectMapper();
    final EmailTemplateService templates=new EmailTemplateService("https://shop.example.test/");
    ObjectNode payload(String type) throws Exception {
        var p=(ObjectNode)json.readTree("""
            {"order":{"order_id":42,"total_payment":132000.35,"sub_total":100000,"tax":10000,"shipping_fee":22000},
            "delivery":{"receiver_name":"<script>&\"'","email":"customer@example.test"},"refundMethod":"VIETQR","refundStatus":"REFUND_PENDING"}
            """.replace("&\"'","&\\\"'"));
        p.put("type",type);((ObjectNode)p.path("order")).put("customer_access_token","a".repeat(64));return p;
    }
    @Test void paymentMoneyEscapingAndOwnedUrl() throws Exception {
        var email=templates.render(payload("ORDER_PAYMENT_SUCCEEDED"));
        assertThat(email.subject()).isEqualTo("AIMS Payment Confirmation - Order #42");
        assertThat(email.text()).isEqualTo("Payment confirmed for AIMS order #42. Total: 132.000,35 VND.");
        assertThat(email.html()).contains("&lt;script&gt;&amp;&quot;&#39;","https://shop.example.test/order-detail?orderId=42&amp;token="+"a".repeat(64),"110.000 VND").doesNotContain("<script>","intent=cancel");
    }
    @Test void lifecycleTemplatesPreserveSubjectsAndRefundDetails() throws Exception {
        assertThat(templates.render(payload("ORDER_APPROVED")).text()).isEqualTo("Your order was approved for AIMS order #42. Your order has been accepted and will be prepared for delivery.");
        assertThat(templates.render(payload("ORDER_REJECTED")).subject()).isEqualTo("AIMS Order Rejected - Order #42");
        var cancelled=templates.render(payload("ORDER_CANCELLED"));
        assertThat(cancelled.subject()).isEqualTo("AIMS Order Cancellation Confirmation - Order #42");
        assertThat(cancelled.text()).isEqualTo("Order #42 was cancelled. Refund status: REFUND_PENDING.");
        assertThat(cancelled.html()).contains("VIETQR","REFUND_PENDING");
    }
    @Test void refusesUnsafeApplicationUrl() {
        for(String url:new String[]{"http://external.test","https://user:secret@example.test","javascript:alert(1)","https://example.test/?token=x"})
            assertThatThrownBy(()->new EmailTemplateService(url)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void sdkReceivesRecipientAndBothContents() throws Exception {
        var sdk=mock(SendGrid.class);var response=new Response();response.setStatusCode(202);when(sdk.api(any())).thenReturn(response);
        new SendGridEmailProvider(templates,sdk).send(payload("ORDER_APPROVED"));
        var arg=ArgumentCaptor.forClass(Request.class);verify(sdk).api(arg.capture());var request=arg.getValue();
        assertThat(request.getMethod()).isEqualTo(Method.POST);assertThat(request.getEndpoint()).isEqualTo("mail/send");
        var body=json.readTree(request.getBody());assertThat(body.at("/personalizations/0/to/0/email").asText()).isEqualTo("customer@example.test");
        assertThat(body.at("/content/0/type").asText()).isEqualTo("text/plain");assertThat(body.at("/content/1/type").asText()).isEqualTo("text/html");
    }
    @Test void sdkErrorsAreSanitizedAndClassified() throws Exception {
        var sdk=mock(SendGrid.class);var provider=new SendGridEmailProvider(templates,sdk);
        for(int status:new int[]{400,401,429,503}) {
            var response=new Response();response.setStatusCode(status);response.setBody("secret provider body");when(sdk.api(any())).thenReturn(response);
            try {provider.send(payload("ORDER_APPROVED"));fail("Expected failure");}catch(NotificationProvider.DeliveryFailure e){assertThat(e.getMessage()).isEqualTo("HTTP_"+status);assertThat(e.retryable).isEqualTo(status==429 || status>=500);}
        }
        when(sdk.api(any())).thenThrow(new java.io.IOException("secret"));
        assertThatThrownBy(()->provider.send(payload("ORDER_APPROVED"))).hasMessage("TRANSPORT_ERROR");
    }
    @Test void disabledOrMissingConfigurationCannotSend() throws Exception {
        var provider=new SendGridEmailProvider(templates,"","","AIMS",false);
        try {assertThat(provider.available()).isFalse();assertThatThrownBy(()->provider.send(payload("ORDER_APPROVED"))).hasMessage("NOT_CONFIGURED");}finally{provider.close();}
    }
}
