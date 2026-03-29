package com.hms.appointment.scheduler;

import com.hms.appointment.client.UserServiceClient;
import com.hms.appointment.entity.Appointment;
import com.hms.appointment.kafka.AppointmentEventPublisher;
import com.hms.appointment.kafka.AppointmentEvents;
import com.hms.appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sends appointment reminders every day at 08:00 for the next day's appointments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReminderScheduler {

    private final AppointmentRepository     appointmentRepository;
    private final AppointmentEventPublisher eventPublisher;
    private final UserServiceClient         userServiceClient;

    @Scheduled(cron = "0 0 8 * * *")
    public void sendReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        log.info("Scheduler: sending appointment reminders for {}", tomorrow);

        List<Appointment> upcoming = appointmentRepository.findUpcomingForDate(tomorrow);
        log.info("Scheduler: found {} upcoming appointments for tomorrow", upcoming.size());

        upcoming.forEach(appt -> {
            String patientEmail = userServiceClient.getUserById(appt.getPatientId())
                .map(UserServiceClient.UserInfo::getEmail).orElse(null);
            String doctorName = userServiceClient.getUserById(appt.getDoctorId())
                .map(UserServiceClient.UserInfo::getFullName).orElse("Doctor #" + appt.getDoctorId());

            eventPublisher.publishReminderEvent(AppointmentEvents.AppointmentReminderEvent.builder()
                .appointmentId(appt.getId())
                .patientId(appt.getPatientId())
                .patientEmail(patientEmail)
                .doctorId(appt.getDoctorId())
                .doctorName(doctorName)
                .appointmentDate(appt.getAppointmentDate())
                .startTime(appt.getStartTime())
                .occurredAt(Instant.now())
                .build());
        });

        log.info("Scheduler: dispatched {} reminder events", upcoming.size());
    }
}
