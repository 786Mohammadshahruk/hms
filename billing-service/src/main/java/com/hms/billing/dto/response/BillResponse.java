package com.hms.billing.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.billing.enums.BillStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillResponse {
    private final Long            id;
    private final UUID            uuid;
    private final Long            patientId;
    private final Long            appointmentId;
    private final BigDecimal      totalAmount;
    private final BigDecimal      paidAmount;
    private final BigDecimal      remainingAmount;
    private final LocalDate       dueDate;
    private final BillStatus      status;
    private final String          description;
    private final List<BillItemResponse> items;
    private final Instant         createdAt;
    private final Instant         updatedAt;
}
