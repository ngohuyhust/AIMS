package vn.aims.cart.service;

/** Available alternative, deliberately not registered as the active strategy. */
public class VolumetricShippingStrategy implements ShippingStrategy {
    @Override public double chargeableWeight(double actualWeight,Dimensions dimensions) {
        if(dimensions==null) return Math.max(actualWeight,0);
        double volume=value(dimensions.length())*value(dimensions.width())*value(dimensions.height())/6000;
        return Math.max(actualWeight,volume);
    }
    private static double value(Double value) { return value==null?0:value; }
}
