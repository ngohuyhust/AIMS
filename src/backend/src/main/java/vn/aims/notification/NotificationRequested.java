package vn.aims.notification;

/** Synchronous request to persist a notification before the surrounding business transaction commits. */
public record NotificationRequested(String type,int orderId,Integer paymentTransactionId,String refundMethod,String refundStatus) {}
