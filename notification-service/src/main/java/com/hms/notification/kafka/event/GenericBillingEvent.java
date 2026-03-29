package com.hms.notification.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenericBillingEvent {
    private Long       billId;
    private Long       patientId;
    private BigDecimal amount;
    private String     dueDate;
    private String     transactionId;
    private String     failureReason;
}
