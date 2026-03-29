package com.hms.notification.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentCancelledEvent {
    private Long   appointmentId;
    private Long   patientId;
    private String appointmentDate;
    private String startTime;
    private String patientEmail;
    private String cancellationReason;
    private String reason; // alias for compatibility
}
