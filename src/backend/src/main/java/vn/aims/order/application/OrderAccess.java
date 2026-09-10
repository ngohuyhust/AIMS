package vn.aims.order.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import vn.aims.auth.JwtTokens;
import vn.aims.order.domain.Order;

/** Capability ownership for guests; a verified PRODUCT_MANAGER JWT permits detail reads only. */
@Component
public class OrderAccess {
    private final JwtTokens jwt;
    public OrderAccess(JwtTokens jwt) { this.jwt=jwt; }
    boolean manager(String authorization) {
        if(authorization==null || !authorization.startsWith("Bearer ")) return false;
        try { return jwt.verify(authorization.substring(7)).getStringListClaim("roles").contains("PRODUCT_MANAGER"); }
        catch(Exception error) { return false; }
    }
    void require(Order order,int id,String token,boolean manager) {
        if(manager) { if(order==null) throw new OrderError(404,"Order with ID "+id+" not found");return; }
        if(token==null || token.isBlank()) throw new OrderError(401,"Missing customer order access token");
        if(order==null || order.getCustomerAccessToken()==null || !MessageDigest.isEqual(order.getCustomerAccessToken().getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
            throw new OrderError(404,"Order with ID "+id+" was not found for this access token");
    }
}
