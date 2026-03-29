package com.hms.medical.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.medical.enums.PrescriptionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrescriptionResponse {
    private final Long               id;
    private final UUID               uuid;
    private final Long               patientId;
    private final Long               doctorId;
    private final Long               appointmentId;
    private final String             diagnosis;
    private final String             notes;
    private final LocalDate          validUntil;
    private final PrescriptionStatus status;
    private final List<MedicineResponse> medicines;
    private final Instant            createdAt;
    private final Instant            updatedAt;
}
