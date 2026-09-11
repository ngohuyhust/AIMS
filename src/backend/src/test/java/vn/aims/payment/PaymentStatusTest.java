package vn.aims.payment;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import vn.aims.payment.service.PaymentService;
import vn.aims.payment.entity.PaymentStatus;

class PaymentStatusTest {
    @Test void onlyThreeStateTransitionsAreAllowed() {
        var allowed=java.util.Set.of("PENDING->SUCCESS","PENDING->FAILED","SUCCESS->REFUNDED");
        for(var from:PaymentStatus.values()) for(var to:PaymentStatus.values())
            assertThat(from.canTransitionTo(to)).as(from+"->"+to).isEqualTo(allowed.contains(from+"->"+to));
    }
    @Test void wholeVndRoundingMatchesApprovedPositiveMoneyRule() {
        assertThat(PaymentService.wholeVnd(new BigDecimal("100.49"))).isEqualByComparingTo("100");
        assertThat(PaymentService.wholeVnd(new BigDecimal("100.50"))).isEqualByComparingTo("101");
        assertThat(PaymentService.wholeVnd(new BigDecimal("100.51"))).isEqualByComparingTo("101");
    }
}
