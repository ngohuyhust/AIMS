package vn.aims.vietqr;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.aims.auth.security.JwtTokens;
import vn.aims.payment.dto.*;
import vn.aims.payment.event.*;
import vn.aims.payment.exception.*;
import vn.aims.payment.service.*;
import vn.aims.vietqr.dto.*;
import vn.aims.vietqr.service.*;
import vn.aims.vietqr.client.*;
import vn.aims.vietqr.gateway.*;
import vn.aims.vietqr.repository.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VietqrTriggerPolicyTest {
    @Test void enabledTriggerRequiresManagerWithoutHoldingTransactionDuringRemoteCallback() {
        var gateway=mock(VietqrGateway.class);var manager=mock(PlatformTransactionManager.class);
        var jwt=new JwtTokens("synthetic-signing-key-for-tests-only-32bytes");
        var service=new VietqrService(mock(VietqrStore.class),gateway,mock(PaymentService.class),mock(VietqrMerchantTokens.class),jwt,true,manager);
        assertThatThrownBy(()->service.trigger(1,null)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(()->service.trigger(1,"Bearer "+jwt.issue(1,"admin@example.test","Admin",List.of("ADMIN")))).isInstanceOf(PaymentException.class);
        verifyNoInteractions(gateway);
        assertThat(service.trigger(1,"Bearer "+jwt.issue(1,"pm@example.test","PM",List.of("PRODUCT_MANAGER")))).containsEntry("status","SUCCESS");
        verify(gateway).trigger(1);verifyNoInteractions(manager);
    }
}
