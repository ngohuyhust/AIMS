package vn.aims.payment.entity;

public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, REFUNDED;
    public boolean canTransitionTo(PaymentStatus next) {
        return this==PENDING && (next==SUCCESS || next==FAILED) || this==SUCCESS && next==REFUNDED;
    }
}
