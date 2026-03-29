package com.hms.appointment.kafka;

import com.hms.appointment.enums.AppointmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public class AppointmentEvents {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AppointmentBookedEvent {
        private Long            appointmentId;
        private UUID            appointmentUuid;
        private Long            patientId;
        private String          patientEmail;
        private Long            doctorId;
        private String          doctorName;
        private LocalDate       appointmentDate;
        private LocalTime       startTime;
        private AppointmentType type;
        private Instant         occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AppointmentCancelledEvent {
        private Long    appointmentId;
        private UUID    appointmentUuid;
        private Long    patientId;
        private String  patientEmail;
        private Long    doctorId;
        private String  cancellationReason;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AppointmentReminderEvent {
        private Long      appointmentId;
        private Long      patientId;
        private String    patientEmail;
        private Long      doctorId;
        private String    doctorName;
        private LocalDate appointmentDate;
        private LocalTime startTime;
        private Instant   occurredAt;
    }
}
