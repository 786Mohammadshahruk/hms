package com.hms.medical.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MedicalEventPublisher {

    private static final String PRESCRIPTION_CREATED  = "prescription.created";
    private static final String TEST_ORDERED           = "test.ordered";
    private static final String TEST_RESULT_UPLOADED   = "test.result.uploaded";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Async("taskExecutor")
    public void publishPrescriptionCreated(MedicalEvents.PrescriptionCreatedEvent event) {
        kafkaTemplate.send(PRESCRIPTION_CREATED, event.getPrescriptionId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish prescription.created: {}", ex.getMessage());
                else            log.info("Published prescription.created for prescriptionId={}", event.getPrescriptionId());
            });
    }

    @Async("taskExecutor")
    public void publishTestOrdered(MedicalEvents.TestOrderedEvent event) {
        kafkaTemplate.send(TEST_ORDERED, event.getTestId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish test.ordered: {}", ex.getMessage());
                else            log.info("Published test.ordered for testId={}", event.getTestId());
            });
    }

    @Async("taskExecutor")
    public void publishTestResultUploaded(MedicalEvents.TestResultUploadedEvent event) {
        kafkaTemplate.send(TEST_RESULT_UPLOADED, event.getTestId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish test.result.uploaded: {}", ex.getMessage());
                else            log.info("Published test.result.uploaded for testId={}", event.getTestId());
            });
    }
}
