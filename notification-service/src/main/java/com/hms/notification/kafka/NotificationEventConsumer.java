package com.hms.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.notification.kafka.event.*;
import com.hms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper        objectMapper;

    @KafkaListener(topics = "user.registered", groupId = "notification-service-group")
    public void onUserRegistered(@Payload Map<String, Object> payload) {
        try {
            UserRegisteredEvent event = objectMapper.convertValue(payload, UserRegisteredEvent.class);
            log.info("Consuming user.registered for userId={}", event.getUserId());

            notificationService.sendEmail(
                    event.getUserId(),
                    event.getEmail(),
                    "user.registered",
                    "Welcome to HMS, " + event.getFirstName() + "!",
                    String.format("Dear %s %s,\n\nYour account has been created successfully. Role: %s\n\nThank you!",
                            event.getFirstName(), event.getLastName(), event.getRole())
            );
        } catch (Exception e) {
            log.error("Error processing user.registered event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "appointment.booked", groupId = "notification-service-group")
    public void onAppointmentBooked(@Payload Map<String, Object> payload) {
        try {
            AppointmentBookedEvent event = objectMapper.convertValue(payload, AppointmentBookedEvent.class);
            log.info("Consuming appointment.booked for appointmentId={}", event.getAppointmentId());

            String patientName = event.getPatientName() != null ? event.getPatientName() : "Patient";
            notificationService.sendEmail(
                    event.getPatientId(),
                    event.getPatientEmail(),
                    "appointment.booked",
                    "Appointment Confirmed - " + event.getAppointmentDate(),
                    String.format("Dear %s,\n\nYour appointment has been confirmed.\nDoctor: %s\nDate: %s\nTime: %s\n\nPlease arrive 10 minutes early.",
                            patientName, event.getDoctorName(), event.getAppointmentDate(), event.getStartTime())
            );
        } catch (Exception e) {
            log.error("Error processing appointment.booked event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "appointment.cancelled", groupId = "notification-service-group")
    public void onAppointmentCancelled(@Payload Map<String, Object> payload) {
        try {
            AppointmentCancelledEvent event = objectMapper.convertValue(payload, AppointmentCancelledEvent.class);
            log.info("Consuming appointment.cancelled for appointmentId={}", event.getAppointmentId());

            notificationService.sendEmail(
                    event.getPatientId(),
                    event.getPatientEmail(),
                    "appointment.cancelled",
                    "Appointment Cancelled",
                    String.format("Your appointment on %s at %s has been cancelled.\nReason: %s",
                            event.getAppointmentDate(), event.getStartTime(),
                            event.getCancellationReason() != null ? event.getCancellationReason() :
                            (event.getReason() != null ? event.getReason() : "N/A"))
            );
        } catch (Exception e) {
            log.error("Error processing appointment.cancelled event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "appointment.reminder", groupId = "notification-service-group")
    public void onAppointmentReminder(@Payload Map<String, Object> payload) {
        try {
            AppointmentReminderEvent event = objectMapper.convertValue(payload, AppointmentReminderEvent.class);
            log.info("Consuming appointment.reminder for appointmentId={}", event.getAppointmentId());

            String patientName = event.getPatientName() != null ? event.getPatientName() : "Patient";
            notificationService.sendEmail(
                    event.getPatientId(),
                    event.getPatientEmail(),
                    "appointment.reminder",
                    "Appointment Reminder - Tomorrow",
                    String.format("Dear %s,\n\nThis is a reminder of your appointment tomorrow.\nDoctor: %s\nDate: %s\nTime: %s",
                            patientName, event.getDoctorName(), event.getAppointmentDate(), event.getStartTime())
            );
        } catch (Exception e) {
            log.error("Error processing appointment.reminder event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "bill.generated", groupId = "notification-service-group")
    public void onBillGenerated(@Payload Map<String, Object> payload) {
        try {
            GenericBillingEvent event = objectMapper.convertValue(payload, GenericBillingEvent.class);
            log.info("Consuming bill.generated for billId={}", event.getBillId());

            notificationService.sendEmail(
                    event.getPatientId(),
                    "patient-" + event.getPatientId() + "@hms.local",
                    "bill.generated",
                    "New Bill Generated - #" + event.getBillId(),
                    String.format("A new bill has been generated.\nBill ID: %s\nAmount: %s\nDue Date: %s",
                            event.getBillId(), event.getAmount(), event.getDueDate())
            );
        } catch (Exception e) {
            log.error("Error processing bill.generated event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment.success", groupId = "notification-service-group")
    public void onPaymentSuccess(@Payload Map<String, Object> payload) {
        try {
            GenericBillingEvent event = objectMapper.convertValue(payload, GenericBillingEvent.class);
            log.info("Consuming payment.success for billId={}", event.getBillId());

            notificationService.sendEmail(
                    event.getPatientId(),
                    "patient-" + event.getPatientId() + "@hms.local",
                    "payment.success",
                    "Payment Received - Thank You",
                    String.format("Your payment of %s has been received.\nTransaction ID: %s\nBill ID: %s",
                            event.getAmount(), event.getTransactionId(), event.getBillId())
            );
        } catch (Exception e) {
            log.error("Error processing payment.success event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment.overdue", groupId = "notification-service-group")
    public void onPaymentOverdue(@Payload Map<String, Object> payload) {
        try {
            GenericBillingEvent event = objectMapper.convertValue(payload, GenericBillingEvent.class);
            log.info("Consuming payment.overdue for billId={}", event.getBillId());

            notificationService.sendEmail(
                    event.getPatientId(),
                    "patient-" + event.getPatientId() + "@hms.local",
                    "payment.overdue",
                    "Payment Overdue - Action Required",
                    String.format("Your bill #%s is overdue.\nRemaining Amount: %s\nPlease make payment as soon as possible.",
                            event.getBillId(), event.getAmount())
            );
        } catch (Exception e) {
            log.error("Error processing payment.overdue event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "test.result.uploaded", groupId = "notification-service-group")
    public void onTestResultUploaded(@Payload Map<String, Object> payload) {
        try {
            log.info("Consuming test.result.uploaded");
            Long patientId = ((Number) payload.get("patientId")).longValue();
            String testName = (String) payload.get("testName");
            boolean abnormal = Boolean.TRUE.equals(payload.get("abnormal"));

            String subject = abnormal
                    ? "ALERT: Abnormal Test Result - " + testName
                    : "Test Result Available - " + testName;
            String body = String.format("Your test result for %s is now available.%s\nPlease consult your doctor.",
                    testName, abnormal ? "\n\nResult flagged as ABNORMAL. Please contact your doctor immediately." : "");

            notificationService.sendEmail(patientId, "patient-" + patientId + "@hms.local",
                    "test.result.uploaded", subject, body);
        } catch (Exception e) {
            log.error("Error processing test.result.uploaded event: {}", e.getMessage(), e);
        }
    }
}
