package com.hms.appointment.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.appointment-booked}")
    private String appointmentBookedTopic;

    @Value("${kafka.topics.appointment-cancelled}")
    private String appointmentCancelledTopic;

    @Value("${kafka.topics.appointment-reminder}")
    private String appointmentReminderTopic;

    @Async("taskExecutor")
    public void publishAppointmentBooked(AppointmentEvents.AppointmentBookedEvent event) {
        send(appointmentBookedTopic, String.valueOf(event.getAppointmentId()), event);
    }

    @Async("taskExecutor")
    public void publishAppointmentCancelled(AppointmentEvents.AppointmentCancelledEvent event) {
        send(appointmentCancelledTopic, String.valueOf(event.getAppointmentId()), event);
    }

    @Async("taskExecutor")
    public void publishReminderEvent(AppointmentEvents.AppointmentReminderEvent event) {
        send(appointmentReminderTopic, String.valueOf(event.getAppointmentId()), event);
    }

    private void send(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to topic '{}' key='{}': {}", topic, key, ex.getMessage());
            } else {
                log.debug("Published to topic '{}' partition={} offset={}",
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}
