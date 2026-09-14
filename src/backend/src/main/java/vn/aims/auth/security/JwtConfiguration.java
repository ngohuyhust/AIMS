package vn.aims.auth.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {
    @Bean
    SecretKey accessTokenKey(@Value("${JWT_SECRET:}") String secret) { return key(secret); }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { return encoder(key); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) { return decoder(key); }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        converter.setPrincipalClaimName("email");
        return converter;
    }

    public static SecretKey key(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    public static JwtEncoder encoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
    }

    public static NimbusJwtDecoder decoder(SecretKey key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, JwtConfiguration::validateClaims));
        return decoder;
    }

    private static OAuth2TokenValidatorResult validateClaims(Jwt jwt) {
        Object id = jwt.getClaim("userID");
        Object roles = jwt.getClaim("roles");
        boolean validID = id instanceof Number number && number.longValue() > 0
                && number.longValue() <= Integer.MAX_VALUE && number.doubleValue() == number.longValue();
        boolean validRoles = roles instanceof Collection<?> values
                && values.stream().allMatch(String.class::isInstance);
        if (jwt.getExpiresAt() != null && validID && validRoles) return OAuth2TokenValidatorResult.success();
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required access-token claims are invalid", null));
    }
}
