package vn.aims.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens {
    private final byte[] secret;
    public JwtTokens(@Value("${JWT_SECRET:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
    }
    public String issue(int userID, String email, String fullName, List<String> roles) {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder().claim("userID",userID).claim("email",email)
                .claim("fullName",fullName).claim("roles",roles).issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(86400))).build();
        try {
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException error) { throw new IllegalStateException("JWT signing failed"); }
    }
    public JWTClaimsSet verify(String token) {
        try {
            var jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret)))
                throw new IllegalArgumentException();
            var claims = jwt.getJWTClaimsSet();
            var now = new Date();
            if (claims.getExpirationTime() == null || !now.before(claims.getExpirationTime())
                    || (claims.getNotBeforeTime() != null && now.before(claims.getNotBeforeTime()))
                    || claims.getIntegerClaim("userID") == null || claims.getIntegerClaim("userID") <= 0
                    || claims.getStringListClaim("roles") == null) throw new IllegalArgumentException();
            return claims;
        } catch (Exception error) { throw new IllegalArgumentException("Invalid access token"); }
    }
}
