package com.hms.appointment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.appointment.enums.AppointmentStatus;
import com.hms.appointment.enums.AppointmentType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppointmentResponse {

    private final Long              id;
    private final UUID              uuid;
    private final Long              patientId;
    private final String            patientName;
    private final Long              doctorId;
    private final String            doctorName;
    private final LocalDate         appointmentDate;
    private final LocalTime         startTime;
    private final LocalTime         endTime;
    private final AppointmentStatus status;
    private final AppointmentType   type;
    private final String            reason;
    private final String            notes;
    private final String            cancellationReason;
    private final Instant           createdAt;
    private final Instant           updatedAt;
}
