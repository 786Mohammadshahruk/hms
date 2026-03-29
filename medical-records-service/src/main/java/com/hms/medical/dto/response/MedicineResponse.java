package com.hms.medical.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MedicineResponse {
    private final Long    id;
    private final String  medicineName;
    private final String  dosage;
    private final String  frequency;
    private final Integer durationDays;
    private final String  instructions;
    private final Integer quantity;
}
