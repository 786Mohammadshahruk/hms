package com.hms.notification.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentReminderEvent {
    private Long   appointmentId;
    private Long   patientId;
    private String appointmentDate;
    private String startTime;
    private String patientEmail;
    private String patientName;   // may be null if not sent by publisher
    private String doctorName;
}
