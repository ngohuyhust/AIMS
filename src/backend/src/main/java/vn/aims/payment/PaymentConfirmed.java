package vn.aims.payment;

/** Published after commit, at most once per successful state transition in the live process. */
public record PaymentConfirmed(int orderId,int paymentTransactionId) {
    public String type() { return "ORDER_PAYMENT_SUCCEEDED"; }
}
