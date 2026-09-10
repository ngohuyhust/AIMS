package vn.aims.order.application;

/** In-process after-commit event; notification delivery is MODULE12. */
public record OrderLifecycleEvent(String type,int orderId,Integer paymentTransactionId,String refundMethod,String refundStatus) {}
