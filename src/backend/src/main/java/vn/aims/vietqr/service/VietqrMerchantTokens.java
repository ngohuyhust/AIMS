package vn.aims.vietqr.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import vn.aims.auth.security.JwtConfiguration;
import vn.aims.payment.exception.PaymentException;

/** Separate signing key and audience: application/user JWTs cannot authenticate bank callbacks. */
@Component
public class VietqrMerchantTokens {
    private final String username,password;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    @Autowired
    public VietqrMerchantTokens(@Value("${VIETQR_MERCHANT_USERNAME:}") String username,
            @Value("${VIETQR_MERCHANT_PASSWORD:}") String password,@Value("${JWT_SECRET:}") String secret) {
        this(username,password,secret,Clock.systemUTC());
    }

    public VietqrMerchantTokens(String username,String password,String secret,Clock clock) {
        this.username=username;this.password=password;this.clock=clock;
        try {
            byte[] root=JwtConfiguration.key(secret).getEncoded();
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(root,"HmacSHA256"));
            var key=new SecretKeySpec(mac.doFinal("aims:merchant:vietqr:v1".getBytes(StandardCharsets.UTF_8)),"HmacSHA256");
            encoder=JwtConfiguration.encoder(key);
            var springDecoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
            var timestamps=new JwtTimestampValidator(Duration.ZERO);timestamps.setClock(clock);
            springDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,this::validate));
            decoder=springDecoder;
        } catch(Exception error) {throw new IllegalStateException("Invalid merchant signing configuration",error);}
    }

    public Map<String,Object> issue(String authorization) {
        try {
            if(username.isBlank() || password.isBlank() || authorization==null || !authorization.startsWith("Basic ")) throw new IllegalArgumentException();
            byte[] supplied=Base64.getDecoder().decode(authorization.substring(6));
            if(!MessageDigest.isEqual(supplied,(username+":"+password).getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException();
            var now=clock.instant();
            var claims=JwtClaimsSet.builder().issuer("aims-vietqr-merchant").subject(username)
                    .audience(List.of("vietqr-callback")).id(UUID.randomUUID().toString())
                    .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
            var headers=JwsHeader.with(MacAlgorithm.HS256).build();
            String value=encoder.encode(JwtEncoderParameters.from(headers,claims)).getTokenValue();
            return Map.of("access_token",value,"token_type","Bearer","expires_in",300);
        } catch(Exception error) {throw new PaymentException(401,"Invalid VietQR merchant credentials");}
    }

    public void verify(String authorization) {
        try {
            if(username.isBlank() || password.isBlank() || authorization==null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
            decoder.decode(authorization.substring(7));
        } catch(Exception error) {throw new PaymentException(401,"Invalid or expired VietQR merchant token");}
    }

    private OAuth2TokenValidatorResult validate(Jwt jwt) {
        var issued=jwt.getIssuedAt();var expires=jwt.getExpiresAt();var now=clock.instant();
        boolean valid="aims-vietqr-merchant".equals(jwt.getClaimAsString("iss"))
                && username.equals(jwt.getSubject()) && jwt.getAudience().equals(List.of("vietqr-callback"))
                && issued!=null && expires!=null && !issued.isAfter(now) && !expires.isAfter(issued.plusSeconds(300));
        if(valid)return OAuth2TokenValidatorResult.success();
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid merchant token claims",null));
    }
}
