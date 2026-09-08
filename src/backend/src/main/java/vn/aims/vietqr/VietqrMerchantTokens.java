package vn.aims.vietqr;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.aims.payment.PaymentException;

/** Separate signing key and audience: application/user JWTs cannot authenticate bank callbacks. */
@Component
public class VietqrMerchantTokens {
    private final String username,password;
    private final byte[] key;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public VietqrMerchantTokens(@Value("${VIETQR_MERCHANT_USERNAME:}") String username,
            @Value("${VIETQR_MERCHANT_PASSWORD:}") String password,@Value("${JWT_SECRET:}") String secret) {
        this(username,password,secret,Clock.systemUTC());
    }
    VietqrMerchantTokens(String username,String password,String secret,Clock clock) {
        this.username=username;this.password=password;this.clock=clock;
        try {
            if(secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException();
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            key=mac.doFinal("aims:merchant:vietqr:v1".getBytes(StandardCharsets.UTF_8));
        } catch(Exception e) {throw new IllegalStateException("Invalid merchant signing configuration");}
    }
    Map<String,Object> issue(String authorization) {
        try {
            if(username.isBlank() || password.isBlank() || authorization==null || !authorization.startsWith("Basic ")) throw new IllegalArgumentException();
            byte[] supplied=Base64.getDecoder().decode(authorization.substring(6));
            if(!MessageDigest.isEqual(supplied,(username+":"+password).getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException();
            var now=clock.instant();var claims=new JWTClaimsSet.Builder().issuer("aims-vietqr-merchant").subject(username).audience("vietqr-callback")
                .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).build();
            var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);jwt.sign(new MACSigner(key));
            return Map.of("access_token",jwt.serialize(),"token_type","Bearer","expires_in",300);
        } catch(Exception e) {throw new PaymentException(401,"Invalid VietQR merchant credentials");}
    }
    void verify(String authorization) {
        try {
            if(username.isBlank() || password.isBlank() || authorization==null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
            var jwt=SignedJWT.parse(authorization.substring(7));var c=jwt.getJWTClaimsSet();var now=clock.instant();
            if(!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(key))
                || !"aims-vietqr-merchant".equals(c.getIssuer()) || !username.equals(c.getSubject()) || !c.getAudience().equals(List.of("vietqr-callback"))
                || c.getIssueTime()==null || c.getExpirationTime()==null || c.getIssueTime().toInstant().isAfter(now) || !now.isBefore(c.getExpirationTime().toInstant())
                || c.getExpirationTime().toInstant().isAfter(c.getIssueTime().toInstant().plusSeconds(300))) throw new IllegalArgumentException();
        } catch(Exception e) {throw new PaymentException(401,"Invalid or expired VietQR merchant token");}
    }
}
