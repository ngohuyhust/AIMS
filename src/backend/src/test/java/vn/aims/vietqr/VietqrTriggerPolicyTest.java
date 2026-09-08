package vn.aims.vietqr;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.aims.auth.JwtTokens;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VietqrTriggerPolicyTest {
    @Test void enabledTriggerRequiresManagerWithoutHoldingTransactionDuringRemoteCallback() {
        var gateway=mock(VietqrGateway.class);var manager=mock(PlatformTransactionManager.class);
        var jwt=new JwtTokens("synthetic-signing-key-for-tests-only-32bytes");
        var service=new VietqrService(mock(VietqrStore.class),gateway,mock(vn.aims.payment.PaymentService.class),mock(VietqrMerchantTokens.class),jwt,true,manager);
        assertThatThrownBy(()->service.trigger(1,null)).isInstanceOf(vn.aims.payment.PaymentException.class);
        assertThatThrownBy(()->service.trigger(1,"Bearer "+jwt.issue(1,"admin@example.test","Admin",List.of("ADMIN")))).isInstanceOf(vn.aims.payment.PaymentException.class);
        verifyNoInteractions(gateway);
        assertThat(service.trigger(1,"Bearer "+jwt.issue(1,"pm@example.test","PM",List.of("PRODUCT_MANAGER")))).containsEntry("status","SUCCESS");
        verify(gateway).trigger(1);verifyNoInteractions(manager);
    }
}
