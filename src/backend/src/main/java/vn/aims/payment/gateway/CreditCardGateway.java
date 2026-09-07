package vn.aims.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import vn.aims.payment.PaymentConfirmation;

/** Active capture/refund modality. No implementation or network calls before MODULE9. */
public interface CreditCardGateway {
    record CaptureResult(boolean completed,PaymentConfirmation confirmation,JsonNode raw) {}
    String method();
    JsonNode createOrder(GatewayRequest request);
    CaptureResult captureOrder(String gatewayOrderId,int orderId);
    JsonNode refund(GatewayRequest request);
}
