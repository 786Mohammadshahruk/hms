package com.hms.notification.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentBookedEvent {
    private Long   appointmentId;
    private Long   patientId;
    private Long   doctorId;
    private String appointmentDate;
    private String startTime;
    private String patientEmail;
    private String patientName;
    private String doctorName;
}
