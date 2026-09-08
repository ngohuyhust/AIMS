package vn.aims.vietqr;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class VietqrApiClientTest {
    HttpServer server;
    ObjectMapper json=new ObjectMapper();
    List<String> auth=new ArrayList<>();List<JsonNode> bodies=new ArrayList<>();
    String mode="normal";
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            var body=exchange.getRequestBody().readAllBytes();if(body.length>0) bodies.add(json.readTree(body));
            String response=exchange.getRequestURI().getPath().endsWith("token_generate")?"{\"data\":{\"accessToken\":\"synthetic-token\"}}":
                "{\"data\":{\"qrDataURL\":\"data:image/png;base64,TEST\",\"transactionID\":\"REMOTE-1\",\"transactionRefID\":\"REF-1\",\"orderID\":\"1\"}}";
            int status=mode.equals("error")?500:200;if(status==500) response="{\"message\":\"NEVER_EXPOSE_PROVIDER_SECRET\"}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
    }
    @AfterEach void stop() {server.stop(0);}
    VietqrApiClient client() {return new VietqrApiClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-user","test-password","VCB","12345678","TEST BANK",json);}
    @Test void preservesPayloadAuthAndNestedResponseAliases() {
        var response=client().generate(1,new BigDecimal("132000"),"AIMS 1");
        assertThat(auth).containsExactly("Basic "+Base64.getEncoder().encodeToString("test-user:test-password".getBytes(StandardCharsets.UTF_8)),"Bearer synthetic-token");
        var body=bodies.getFirst();assertThat(body.path("orderId").asText()).isEqualTo("1");assertThat(body.path("amount").asInt()).isEqualTo(132000);
        assertThat(body.path("bankAccount").asText()).isEqualTo("12345678");assertThat(body.path("transType").asText()).isEqualTo("C");assertThat(body.path("qrType").asInt()).isZero();
        assertThat(response.path("transactionRefId").asText()).isEqualTo("REF-1");assertThat(response.path("qrLink")).isEqualTo(response.path("qrCode"));
    }
    @Test void sanitizesErrorsAndRejectsInsecureOrFakeSandboxHost() {
        mode="error";assertThatThrownBy(()->client().generate(1,BigDecimal.ONE,"AIMS 1")).isInstanceOf(vn.aims.payment.PaymentException.class).hasMessageNotContaining("NEVER_EXPOSE");
        assertThatThrownBy(()->new VietqrApiClient("http://example.test","a","b","c","d","e",json)).isInstanceOf(IllegalArgumentException.class);
        var remote=new VietqrApiClient("https://dev.vietqr.org.evil.test","a","b","c","d","e",json);
        assertThat(remote.sandbox()).isFalse();
        assertThatThrownBy(()->remote.trigger("AIMS 1",BigDecimal.ONE)).isInstanceOf(vn.aims.payment.PaymentException.class).hasMessageContaining("only available");
    }
}
