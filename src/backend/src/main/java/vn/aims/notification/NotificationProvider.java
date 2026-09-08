package vn.aims.notification;

import com.fasterxml.jackson.databind.JsonNode;

public interface NotificationProvider {
    String channel();
    boolean available();
    void send(JsonNode snapshot) throws DeliveryFailure;
    final class DeliveryFailure extends Exception {
        public final boolean retryable;
        public DeliveryFailure(String code,boolean retryable) {super(code);this.retryable=retryable;}
    }
}
