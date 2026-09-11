package vn.aims.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Optional;
import vn.aims.payment.dto.PaymentConfirmation;

/** Passive bank-confirmation modality, without a refund method; concrete verification is MODULE10. */
public interface QrCodeGateway {
    record CallbackResult(boolean changed,PaymentConfirmation confirmation,String message) {}
    record StatusResult(JsonNode response,boolean expired,int paymentTransactionId) {}
    String method();
    Optional<JsonNode> findReusablePending(int orderId,BigDecimal amount);
    JsonNode createPayment(GatewayRequest request);
    CallbackResult handleCallback(JsonNode authenticatedProviderPayload);
    StatusResult getStatusByPaymentId(int paymentId);
    StatusResult getStatusByTransactionRef(String transactionRef);
}
