package vn.aims.auth.security;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    @Autowired
    public JwtTokens(JwtEncoder encoder, JwtDecoder decoder) { this.encoder=encoder;this.decoder=decoder; }
    public JwtTokens(String secret) {
        var key=JwtConfiguration.key(secret);this.encoder=JwtConfiguration.encoder(key);this.decoder=JwtConfiguration.decoder(key);
    }
    public String issue(int userID, String email, String fullName, List<String> roles) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().claim("userID",userID).claim("email",email)
                .claim("fullName",fullName).claim("roles",roles).issuedAt(now)
                .expiresAt(now.plusSeconds(86400)).build();
        try {
            var headers=JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
            return encoder.encode(JwtEncoderParameters.from(headers,claims)).getTokenValue();
        } catch (RuntimeException error) { throw new IllegalStateException("JWT signing failed",error); }
    }
    public Jwt verify(String token) {
        try { return decoder.decode(token); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Invalid access token",error); }
    }
}
