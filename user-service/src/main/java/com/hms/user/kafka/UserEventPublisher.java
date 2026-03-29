package com.hms.user.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes domain events from User Service to Kafka.
 * All publishes are async — failures are logged but do not roll back
 * the primary transaction (fire-and-forget with callback logging).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.user-registered}")
    private String userRegisteredTopic;

    @Value("${kafka.topics.user-updated}")
    private String userUpdatedTopic;

    @Value("${kafka.topics.password-reset}")
    private String passwordResetTopic;

    // ── Publish methods ────────────────────────────────────────────────────────

    @Async("taskExecutor")
    public void publishUserRegistered(UserEvents.UserRegisteredEvent event) {
        send(userRegisteredTopic, String.valueOf(event.getUserId()), event);
    }

    @Async("taskExecutor")
    public void publishUserUpdated(UserEvents.UserUpdatedEvent event) {
        send(userUpdatedTopic, String.valueOf(event.getUserId()), event);
    }

    @Async("taskExecutor")
    public void publishPasswordResetRequested(UserEvents.PasswordResetRequestedEvent event) {
        send(passwordResetTopic, String.valueOf(event.getUserId()), event);
    }

    // ── Private helper ─────────────────────────────────────────────────────────

    private void send(String topic, String key, Object payload) {
        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic '{}' with key '{}': {}",
                    topic, key, ex.getMessage());
            } else {
                log.debug("Published event to topic '{}' partition={} offset={}",
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}
