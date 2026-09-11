package vn.aims.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import vn.aims.payment.dto.PaymentConfirmation;

/** Active modality; separate transport from verification so outcomes can be journaled first. */
public interface CreditCardGateway {
    String method();
    JsonNode createOrder(GatewayRequest request,java.util.UUID requestId);
    JsonNode captureOrder(String gatewayOrderId,java.util.UUID requestId);
    JsonNode refund(String captureId,int orderId,java.util.UUID requestId);
    JsonNode getOrder(String gatewayOrderId);
    JsonNode getRefund(String refundId);
    PaymentConfirmation verifyCapture(GatewayRequest request,String gatewayOrderId,JsonNode raw);
}
