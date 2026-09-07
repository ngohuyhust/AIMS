package vn.aims.payment.gateway;

import java.math.BigDecimal;

/** Transaction ID is the stable provider idempotency identity; amount is validated whole VND. */
public record GatewayRequest(int orderId,int paymentTransactionId,BigDecimal amount,String content) {}
