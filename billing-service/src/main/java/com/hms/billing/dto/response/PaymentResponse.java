package com.hms.billing.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.billing.enums.PaymentMethod;
import com.hms.billing.enums.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {
    private final Long          id;
    private final UUID          uuid;
    private final Long          billId;
    private final Long          patientId;   // derived from bill.patientId
    private final BigDecimal    amount;
    private final PaymentStatus paymentStatus;
    private final PaymentMethod paymentMethod;
    private final String        transactionRef;
    private final Instant       paidAt;
    private final Instant       createdAt;
}
