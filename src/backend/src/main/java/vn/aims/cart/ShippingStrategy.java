package vn.aims.cart;

public interface ShippingStrategy {
    record Dimensions(Double length,Double width,Double height) {}
    double chargeableWeight(double actualWeight,Dimensions dimensions);
}
