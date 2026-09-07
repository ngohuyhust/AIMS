package vn.aims.payment;

import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Shared payment data only; no order capability, PII or gateway credentials. */
public record PaymentView(@JsonProperty("transactionID") int transactionID,Integer orderId,String method,
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount,String transactionContent,PaymentStatus status,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",timezone="UTC") Instant createdAt) {}
