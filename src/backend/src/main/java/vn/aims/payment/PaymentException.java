package vn.aims.payment;

/** Domain failure for future controller adapters; no payment HTTP routes in MODULE8. */
public class PaymentException extends RuntimeException {
    private final int status;
    public PaymentException(int status,String message) { super(message);this.status=status; }
    public int status() { return status; }
}
