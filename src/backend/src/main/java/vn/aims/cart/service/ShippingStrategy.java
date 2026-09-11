package vn.aims.cart.service;

public interface ShippingStrategy {
    record Dimensions(Double length,Double width,Double height) {}
    double chargeableWeight(double actualWeight,Dimensions dimensions);
}
