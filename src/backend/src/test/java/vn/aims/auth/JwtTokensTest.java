package vn.aims.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import vn.aims.auth.security.*;

class JwtTokensTest {
    final String key = UUID.randomUUID().toString()+UUID.randomUUID();
    final JwtTokens tokens = new JwtTokens(key);
    String signed(JWSAlgorithm alg, Instant expiry, Instant notBefore, String secret) throws Exception {
        var claims = new JWTClaimsSet.Builder().claim("userID",1).claim("roles",List.of("ADMIN"))
                .expirationTime(Date.from(expiry)).notBeforeTime(Date.from(notBefore)).build();
        var jwt = new SignedJWT(new JWSHeader(alg),claims);
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }
    String signedWithoutExpiry() throws Exception {
        var claims = new JWTClaimsSet.Builder().claim("userID",1).claim("roles",List.of("ADMIN")).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }
    @Test void failsClosedWithoutStrongConfiguredKey() {
        assertThatThrownBy(() -> new JwtTokens("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokens("short")).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsExpiryFutureNbfWrongKeyAndWrongAlgorithm() throws Exception {
        Instant now=Instant.now();
        for (String bad : List.of(signed(JWSAlgorithm.HS256,now.minusSeconds(1),now.minusSeconds(5),key),
                signed(JWSAlgorithm.HS256,now.plusSeconds(100),now.plusSeconds(100),key),
                signed(JWSAlgorithm.HS256,now.plusSeconds(100),now.minusSeconds(5),UUID.randomUUID().toString()+UUID.randomUUID()),
                signed(JWSAlgorithm.HS512,now.plusSeconds(100),now.minusSeconds(5),key),
                signedWithoutExpiry(),
                "eyJhbGciOiJub25lIn0.eyJ1c2VySUQiOjF9."))
            assertThatThrownBy(() -> tokens.verify(bad)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void springBcryptEncodesAndMatchesPasswords() {
        var bcrypt = new BCryptPasswordEncoder();
        var encoded = bcrypt.encode("correct");
        assertThat(bcrypt.matches("correct",encoded)).isTrue();
        assertThat(bcrypt.matches("wrong",encoded)).isFalse();
    }
}
