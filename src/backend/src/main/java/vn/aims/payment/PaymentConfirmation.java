package vn.aims.payment;

import java.math.BigDecimal;

/** Internal evidence from a gateway adapter AFTER it verifies authenticity/capture and currency. */
public record PaymentConfirmation(int paymentTransactionId,int orderId,String method,BigDecimal amount) {}
