package com.hms.billing.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class DummyPaymentGateway {

    @Value("${payment.gateway.success-rate:0.95}")
    private double successRate;

    public PaymentResult process(BigDecimal amount, String paymentMethod, Long patientId) {
        log.info("DummyPaymentGateway: processing {} {} for patientId={}", amount, paymentMethod, patientId);

        boolean success = Math.random() < successRate;
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if (success) {
            log.info("DummyPaymentGateway: payment SUCCESS transactionId={}", transactionId);
            return new PaymentResult(true, transactionId, null);
        } else {
            String reason = "Payment declined by issuing bank (simulated)";
            log.warn("DummyPaymentGateway: payment FAILED reason={}", reason);
            return new PaymentResult(false, null, reason);
        }
    }

    public record PaymentResult(boolean success, String transactionId, String failureReason) {}
}
