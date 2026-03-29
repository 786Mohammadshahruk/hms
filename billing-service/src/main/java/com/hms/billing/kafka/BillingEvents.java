package com.hms.billing.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public class BillingEvents {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillGeneratedEvent {
        private Long       billId;
        private Long       patientId;
        private BigDecimal totalAmount;
        private String     dueDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSuccessEvent {
        private Long       paymentId;
        private Long       billId;
        private Long       patientId;
        private BigDecimal amount;
        private String     transactionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentFailedEvent {
        private Long       paymentId;
        private Long       billId;
        private Long       patientId;
        private BigDecimal amount;
        private String     failureReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentOverdueEvent {
        private Long       billId;
        private Long       patientId;
        private BigDecimal remainingAmount;
        private String     dueDate;
    }
}
