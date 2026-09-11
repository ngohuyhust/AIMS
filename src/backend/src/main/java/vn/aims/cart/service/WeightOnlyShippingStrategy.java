package vn.aims.cart.service;

/** Current source binding; dimensions do not affect checkout quotes. */
@org.springframework.stereotype.Component
public class WeightOnlyShippingStrategy implements ShippingStrategy {
    @Override public double chargeableWeight(double actualWeight,Dimensions dimensions) { return actualWeight; }
}
