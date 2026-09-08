package vn.aims.integration;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.aims.notification.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @SpringBootTest @ActiveProfiles("production")
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class CheckoutJourneyTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
    static final ObjectMapper JSON=new ObjectMapper();
    static final HttpServer SERVER=server();
    static HttpServer server() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String response;
                if(exchange.getRequestURI().getPath().endsWith("token_generate"))response="{\"access_token\":\"synthetic\"}";
                else {var body=JSON.readTree(exchange.getRequestBody());response=JSON.createObjectNode().put("qrCode","000201TEST").put("transactionRefId","REF-"+body.path("orderId").asText()).put("orderId",body.path("orderId").asText()).toString();}
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();return server;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    @DynamicPropertySource static void config(DynamicPropertyRegistry p) {
        p.add("AIMS_DB_URL",DB::getJdbcUrl);p.add("AIMS_DB_USERNAME",DB::getUsername);p.add("AIMS_DB_PASSWORD",DB::getPassword);
        p.add("APP_PUBLIC_URL",()->"https://shop.example.test");p.add("VIETQR_API_BASE_URL",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());
        p.add("VIETQR_USERNAME",()->"test");p.add("VIETQR_PASSWORD",()->"test");p.add("VIETQR_BANK_CODE",()->"VCB");p.add("VIETQR_BANK_ACCOUNT",()->"12345678");p.add("VIETQR_BANK_ACCOUNT_NAME",()->"TEST");
        p.add("VIETQR_MERCHANT_USERNAME",()->"merchant");p.add("VIETQR_MERCHANT_PASSWORD",()->"synthetic");
    }
    @AfterAll static void stop() {SERVER.stop(0);}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;@Autowired NotificationDispatcher dispatcher;
    @MockitoBean SendGridEmailProvider email;
    String manager;
    @BeforeEach void seed() throws Exception {
        when(email.channel()).thenReturn("EMAIL");when(email.available()).thenReturn(true);
        jdbc.update("DELETE FROM orders");jdbc.update("DELETE FROM product_logs");jdbc.update("DELETE FROM products");jdbc.update("DELETE FROM user_audit_logs");jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO products(product_id,product_type,title,category,barcode,weight,original_value,current_price,quantity_in_stock) VALUES (1,'BOOK','Journey book','Book','journey',0.5,50000,50000,3)");
        int id=jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name) VALUES ('manager@example.test',?,'Manager') RETURNING user_id",Integer.class,passwords.encode("test-password"));
        jdbc.update("INSERT INTO users_roles SELECT ?,role_id FROM roles WHERE name='PRODUCT_MANAGER'",id);
        manager="Bearer "+request(post("/api/auth/login").content("{\"email\":\"manager@example.test\",\"password\":\"test-password\"}"),201).path("token").asText();
    }
    JsonNode request(MockHttpServletRequestBuilder request,int status) throws Exception {
        return JSON.readTree(mvc.perform(request.contentType("application/json")).andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
    }
    JsonNode place() throws Exception {
        assertThat(request(get("/api/products?keyword=Journey"),200).size()).isEqualTo(1);
        assertThat(request(post("/api/orders/cart/check-stock").content("{\"cartItems\":[{\"productId\":1,\"quantity\":1}]}"),201).path("available").asBoolean()).isTrue();
        return request(post("/api/orders").content("""
            {"cartItems":[{"productId":1,"quantity":1}],"deliveryInfo":{"receiverName":"Customer","email":"customer@example.test","phoneNumber":"0912345678","address":"Test address","province":"Hà Nội"}}
            """),201);
    }
    void pay(JsonNode order) throws Exception {
        int id=order.path("orderID").asInt();String token=order.path("customerAccessToken").asText();
        var qr=request(post("/api/vietqr/payments").header("x-order-token",token).content(JSON.createObjectNode().put("orderId",id).put("amount",new java.math.BigDecimal(order.path("totalPayment").asText())).put("content","AIMS "+id).toString()),201);
        var credential="Basic "+Base64.getEncoder().encodeToString("merchant:synthetic".getBytes(StandardCharsets.UTF_8));
        var merchant=request(post("/vqr/api/token_generate").header("Authorization",credential),201).path("access_token").asText();
        var callback=JSON.createObjectNode().put("orderId",Integer.toString(id)).put("amount",new java.math.BigDecimal(order.path("totalPayment").asText())).put("bankaccount","12345678").put("content","AIMS "+id).put("transType","C").put("transactionid","BANK-"+id).put("referencenumber",qr.path("transactionRef").asText()).put("transactiontime",Instant.now().toEpochMilli());
        for(int n=0;n<2;n++)request(post("/vqr/bank/api/transaction-callback").header("Authorization","Bearer "+merchant).content(callback.toString()),201);
        assertThat(request(get("/api/orders/"+id).header("x-order-token",token),200).path("status").asText()).isEqualTo("PENDING_PROCESSING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox",Integer.class)).isEqualTo(1);
    }
    @Test void checkoutPaymentApprovalAndEmailComposeAcrossModules() throws Exception {
        var order=place();pay(order);int id=order.path("orderID").asInt();
        assertThat(request(get("/api/orders/pending").header("Authorization",manager),200).path("total").asInt()).isEqualTo(1);
        assertThat(request(post("/api/orders/"+id+"/approve").header("Authorization",manager),201).path("status").asText()).isEqualTo("APPROVED");
        dispatcher.dispatchOne();dispatcher.dispatchOne();assertThat(dispatcher.dispatchOne()).isFalse();verify(email,times(2)).send(any());
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products",Integer.class)).isEqualTo(2);
    }
    @Test void paidCustomerDeniedAndManagerRefundRestoresStockOnce() throws Exception {
        var order=place();pay(order);int id=order.path("orderID").asInt();
        request(post("/api/customer/orders/"+id+"/cancel").param("token",order.path("customerAccessToken").asText()),403);
        assertThat(request(post("/api/orders/"+id+"/cancel").header("Authorization",manager),201).path("status").asText()).isEqualTo("REFUND_PENDING");
        for(int n=0;n<2;n++)assertThat(request(post("/api/orders/"+id+"/confirm-vietqr-refund").header("Authorization",manager),201).path("status").asText()).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox",Integer.class)).isEqualTo(3);
    }
    @Test void unpaidOwnedCancellationAndCorsRemainCompatible() throws Exception {
        var order=place();int id=order.path("orderID").asInt();
        for(int n=0;n<2;n++)request(post("/api/customer/orders/"+id+"/cancel").param("token",order.path("customerAccessToken").asText()),201);
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox",Integer.class)).isEqualTo(1);
        mvc.perform(options("/api/vietqr/payments").header("Origin","http://localhost:4200").header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","x-order-token,content-type"))
            .andExpect(status().isNoContent()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:4200")).andExpect(header().string("Cache-Control","no-store"));
    }
}
