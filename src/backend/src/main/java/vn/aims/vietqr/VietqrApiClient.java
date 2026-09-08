package vn.aims.vietqr;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import vn.aims.payment.PaymentException;

@Component
public class VietqrApiClient {
    private final RestClient client;
    private final String username,password,bankCode,bankAccount,bankName;
    private final boolean sandbox;
    private final ObjectMapper json;
    public VietqrApiClient(@Value("${VIETQR_API_BASE_URL:https://dev.vietqr.org}") String base,
        @Value("${VIETQR_USERNAME:}") String username,@Value("${VIETQR_PASSWORD:}") String password,
        @Value("${VIETQR_BANK_CODE:}") String bankCode,@Value("${VIETQR_BANK_ACCOUNT:}") String bankAccount,
        @Value("${VIETQR_BANK_ACCOUNT_NAME:}") String bankName,ObjectMapper json) {
        var uri=URI.create(base);String host=uri.getHost();boolean loopback=java.util.Set.of("localhost","127.0.0.1","[::1]").contains(host==null?"":host);
        if(host==null || uri.getUserInfo()!=null || !("https".equals(uri.getScheme()) || (loopback && "http".equals(uri.getScheme())))) throw new IllegalArgumentException("VietQR base URL must use HTTPS or loopback HTTP");
        sandbox=host.equals("dev.vietqr.org") || loopback;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(10));
        client=RestClient.builder().baseUrl(base.replaceAll("/+$","")).requestFactory(factory).build();
        this.username=username;this.password=password;this.bankCode=bankCode;this.bankAccount=bankAccount;this.bankName=bankName;this.json=json;
    }
    void configured() {if(username.isBlank() || password.isBlank() || bankCode.isBlank() || bankAccount.isBlank() || bankName.isBlank()) throw new PaymentException(400,"VietQR API credentials or bank account configuration are missing");}
    String bankAccount() {return bankAccount;}
    String bankCode() {return bankCode;}
    String bankName() {return bankName;}
    boolean sandbox() {return sandbox;}
    private String token() {
        configured();
        try {
            var data=client.post().uri("/vqr/api/token_generate").headers(h->h.setBasicAuth(username,password,java.nio.charset.StandardCharsets.UTF_8)).contentType(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class);
            if(data==null || data.path("status").asText().equals("FAILED")) throw new PaymentException(502,"Invalid VietQR token response");
            for(var root:java.util.List.of(data,data.path("data"))) for(String key:java.util.List.of("access_token","accessToken")) if(root.path(key).isTextual() && !root.path(key).asText().isBlank()) return root.path(key).asText();
            throw new PaymentException(502,"Invalid VietQR token response");
        } catch(RestClientException e) {throw new PaymentException(502,"VietQR token request failed");}
    }
    private JsonNode post(String path,Object body) {
        var token=token();
        try {
            var data=client.post().uri(path).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            if(data==null || !data.isObject() || data.path("status").asText().equals("FAILED")) throw new PaymentException(502,"Invalid VietQR response");return data;
        } catch(RestClientResponseException e) {throw new PaymentException(502,"VietQR request failed (HTTP "+e.getStatusCode().value()+")");}
        catch(RestClientException e) {throw new PaymentException(503,"VietQR request outcome unknown; retry the same payment");}
    }
    JsonNode generate(int orderId,BigDecimal amount,String content) {
        var data=post("/vqr/api/qr/generate-customer",Map.of("bankCode",bankCode,"bankAccount",bankAccount,"userBankName",bankName,"amount",amount,"content",content,"orderId",Integer.toString(orderId),"qrType",0,"transType","C"));
        var root=data.path("data").isObject()?data.path("data"):data;var result=json.createObjectNode();
        result.set("qrCode",first(root,"qrCode","qrDataURL"));result.set("qrLink",first(root,"qrLink","qrDataURL"));
        result.set("transactionId",first(root,"transactionId","transactionID"));result.set("transactionRefId",first(root,"transactionRefId","transactionRefID"));
        var returnedOrder=first(root,"orderId","orderID");
        if(!returnedOrder.isNull() && !returnedOrder.asText().equals(Integer.toString(orderId))) throw new PaymentException(502,"VietQR response order does not match");
        if(result.path("qrCode").isNull() && result.path("qrLink").isNull()) throw new PaymentException(502,"VietQR response has no QR code");
        for(String field:java.util.List.of("transactionId","transactionRefId")) if(!result.path(field).isNull() && result.path(field).asText().length()>100) throw new PaymentException(502,"Invalid VietQR reference");
        return result;
    }
    private JsonNode first(JsonNode root,String a,String b) {
        var value=root.get(a);if(value==null || value.isNull()) value=root.get(b);
        if(value==null || value.isNull()) return com.fasterxml.jackson.databind.node.NullNode.instance;
        if(!value.isTextual()) throw new PaymentException(502,"Invalid VietQR response field");return value;
    }
    void trigger(String content,BigDecimal amount) {
        if(!sandbox) throw new PaymentException(400,"Test callback is only available in the dev/sandbox environment");
        post("/vqr/bank/api/test/transaction-callback",Map.of("bankAccount",bankAccount,"content",content,"amount",amount,"bankCode",bankCode,"transType","C"));
    }
}
