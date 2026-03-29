package com.hms.billing.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingEventPublisher {

    private static final String BILL_GENERATED   = "bill.generated";
    private static final String PAYMENT_SUCCESS  = "payment.success";
    private static final String PAYMENT_FAILED   = "payment.failed";
    private static final String PAYMENT_OVERDUE  = "payment.overdue";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Async("taskExecutor")
    public void publishBillGenerated(BillingEvents.BillGeneratedEvent event) {
        kafkaTemplate.send(BILL_GENERATED, event.getBillId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish bill.generated: {}", ex.getMessage());
                else            log.info("Published bill.generated for billId={}", event.getBillId());
            });
    }

    @Async("taskExecutor")
    public void publishPaymentSuccess(BillingEvents.PaymentSuccessEvent event) {
        kafkaTemplate.send(PAYMENT_SUCCESS, event.getPaymentId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish payment.success: {}", ex.getMessage());
                else            log.info("Published payment.success for paymentId={}", event.getPaymentId());
            });
    }

    @Async("taskExecutor")
    public void publishPaymentFailed(BillingEvents.PaymentFailedEvent event) {
        kafkaTemplate.send(PAYMENT_FAILED, event.getPaymentId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish payment.failed: {}", ex.getMessage());
                else            log.info("Published payment.failed for paymentId={}", event.getPaymentId());
            });
    }

    @Async("taskExecutor")
    public void publishPaymentOverdue(BillingEvents.PaymentOverdueEvent event) {
        kafkaTemplate.send(PAYMENT_OVERDUE, event.getBillId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish payment.overdue: {}", ex.getMessage());
                else            log.info("Published payment.overdue for billId={}", event.getBillId());
            });
    }
}
