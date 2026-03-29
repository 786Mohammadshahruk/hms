package com.hms.medical.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.medical.enums.TestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedicalTestResponse {
    private final Long       id;
    private final UUID       uuid;
    private final Long       patientId;
    private final Long       doctorId;
    private final Long       prescriptionId;
    private final String     testName;
    private final String     testType;
    private final TestStatus status;
    private final Instant    orderedAt;
    private final Instant    resultDate;
    private final String     resultValue;
    private final Boolean    isAbnormal;
    private final String     labNotes;
    private final Instant    createdAt;
    private final Instant    updatedAt;
}
