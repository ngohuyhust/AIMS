package vn.aims.paypal.client;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import vn.aims.payment.exception.PaymentException;

/** OAuth and bounded HTTPS I/O only. Never follows redirects or propagates provider error bodies. */
@Component
public class PaypalApiClient {
    private final RestClient client;
    private final String clientId,secret,frontend;
    private String token;
    private Instant tokenExpires=Instant.EPOCH;
    public PaypalApiClient(@Value("${PAYPAL_API_BASE_URL:https://api-m.sandbox.paypal.com}") String base,
            @Value("${PAYPAL_CLIENT_ID:}") String clientId,@Value("${PAYPAL_CLIENT_SECRET:}") String secret,
            @Value("${APP_PUBLIC_URL:http://localhost:4200}") String frontend) {
        URI uri=URI.create(base);
        if(uri.getUserInfo()!=null || uri.getHost()==null || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && java.util.Set.of("127.0.0.1","localhost","[::1]").contains(uri.getHost()))))
            throw new IllegalArgumentException("PayPal base URL must use HTTPS or loopback HTTP");
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client=RestClient.builder().baseUrl(base.replaceAll("/+$","")).requestFactory(factory).build();
        this.clientId=clientId;this.secret=secret;this.frontend=frontend.replaceAll("/+$","");
    }
    public void configured() {
        if(clientId.isBlank() || secret.isBlank()) throw new PaymentException(400,"PayPal API credentials are not configured in environment variables");
    }
    public String frontend() { return frontend; }
    private synchronized String token() {
        configured();if(token!=null && Instant.now().isBefore(tokenExpires)) return token;
        try {
            var json=client.post().uri("/v1/oauth2/token").headers(h->h.setBasicAuth(clientId,secret,java.nio.charset.StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body("grant_type=client_credentials").retrieve().body(JsonNode.class);
            if(json==null || !json.path("access_token").isTextual() || json.path("access_token").asText().isBlank() || json.path("expires_in").asLong()<=0)
                throw new PaymentException(502,"Invalid PayPal OAuth response");
            token=json.path("access_token").asText();tokenExpires=Instant.now().plusSeconds(Math.max(0,Math.min(json.path("expires_in").asLong(),86400)-30));return token;
        } catch(RestClientException error) { throw new PaymentException(502,"Failed to retrieve PayPal access token"); }
    }
    public JsonNode call(HttpMethod method,String path,UUID requestId,JsonNode body) {
        String bearer=token();
        try {
            var request=client.method(method).uri(path).headers(h->{h.setBearerAuth(bearer);h.set("Prefer","return=representation");if(requestId!=null) h.set("PayPal-Request-Id",requestId.toString());});
            if(method==HttpMethod.POST) request.contentType(MediaType.APPLICATION_JSON);
            if(body!=null) request.body(body);
            var result=request.retrieve().body(JsonNode.class);
            if(result==null || !result.isObject()) throw new PaymentException(502,"Invalid PayPal response");return result;
        } catch(RestClientResponseException error) {
            if(error.getStatusCode().value()==401) synchronized(this) { token=null; }
            throw new PaymentException(502,"PayPal request failed (HTTP "+error.getStatusCode().value()+")");
        } catch(RestClientException error) { throw new PaymentException(503,"PayPal request outcome unknown; retry the same operation"); }
    }
}
