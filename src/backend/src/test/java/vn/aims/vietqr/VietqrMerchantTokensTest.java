package vn.aims.vietqr;

import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import vn.aims.vietqr.service.VietqrMerchantTokens;
import vn.aims.payment.exception.PaymentException;
import vn.aims.auth.security.JwtTokens;

class VietqrMerchantTokensTest {
    final String key="synthetic-test-signing-key-32bytes-only";
    final String basic="Basic "+Base64.getEncoder().encodeToString("merchant:password".getBytes(StandardCharsets.UTF_8));
    @Test void expiresAt300SecondsAndCannotActAsUserJwt() {
        var now=Instant.now();var tokens=new VietqrMerchantTokens("merchant","password",key,Clock.fixed(now,ZoneOffset.UTC));
        String value=(String)tokens.issue(basic).get("access_token");tokens.verify("Bearer "+value);
        var later=new VietqrMerchantTokens("merchant","password",key,Clock.fixed(now.plusSeconds(300),ZoneOffset.UTC));
        assertThatThrownBy(()->later.verify("Bearer "+value)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(()->new JwtTokens(key).verify(value)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsWrongCredentialsSignatureAndUnconfiguredMerchant() {
        var tokens=new VietqrMerchantTokens("merchant","password",key);
        assertThatThrownBy(()->tokens.issue("Basic invalid")).isInstanceOf(PaymentException.class);
        assertThatThrownBy(()->new VietqrMerchantTokens("","",key).issue(basic)).isInstanceOf(PaymentException.class);
        String value=(String)tokens.issue(basic).get("access_token");
        assertThatThrownBy(()->new VietqrMerchantTokens("merchant","password",key+"other").verify("Bearer "+value)).isInstanceOf(PaymentException.class);
    }
}
