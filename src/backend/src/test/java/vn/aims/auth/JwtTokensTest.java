package vn.aims.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;
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
                "eyJhbGciOiJub25lIn0.eyJ1c2VySUQiOjF9."))
            assertThatThrownBy(() -> tokens.verify(bad)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bcryptAcceptsOriginalBcryptjsHashAndRetainsByteLimit() {
        var bcrypt = new LegacyBcryptPasswordEncoder();
        // Synthetic oracle produced by the original bcryptjs package, never an account credential.
        assertThat(bcrypt.matches("legacy-password","$2b$10$KSr.MT0G6elLNH3QkIufpujxTCDnPwM/9rZ/GriH8Y7LX2fT9lrZi")).isTrue();
        String prefix="x".repeat(71)+"ế";
        assertThat(bcrypt.matches(prefix+"b",bcrypt.encode(prefix+"a"))).isTrue();
        assertThat(bcrypt.matches("wrong",bcrypt.encode("correct"))).isFalse();
    }
}
