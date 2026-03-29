package com.hms.billing.service.impl;

import com.hms.billing.dto.request.ProcessPaymentRequest;
import com.hms.billing.dto.response.PagedResponse;
import com.hms.billing.dto.response.PaymentResponse;
import com.hms.billing.entity.Bill;
import com.hms.billing.entity.Payment;
import com.hms.billing.enums.BillStatus;
import com.hms.billing.enums.PaymentStatus;
import com.hms.billing.exception.BadRequestException;
import com.hms.billing.exception.ResourceNotFoundException;
import com.hms.billing.gateway.DummyPaymentGateway;
import com.hms.billing.kafka.BillingEventPublisher;
import com.hms.billing.kafka.BillingEvents;
import com.hms.billing.repository.BillRepository;
import com.hms.billing.repository.PaymentRepository;
import com.hms.billing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository     paymentRepository;
    private final BillRepository        billRepository;
    private final DummyPaymentGateway   paymentGateway;
    private final BillingEventPublisher eventPublisher;

    @Override
    @Transactional
    @CacheEvict(value = "bills", key = "#request.billId")
    public PaymentResponse processPayment(Long requestingUserId, ProcessPaymentRequest req) {
        Bill bill = billRepository.findById(req.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + req.getBillId()));

        if (bill.getStatus() == BillStatus.PAID) {
            throw new BadRequestException("Bill is already fully paid");
        }
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new BadRequestException("Cannot pay a cancelled bill");
        }

        BigDecimal remaining = bill.getTotalAmount().subtract(bill.getPaidAmount());
        if (req.getAmount().compareTo(remaining) > 0) {
            throw new BadRequestException("Payment amount exceeds remaining balance of " + remaining);
        }

        Payment payment = Payment.builder()
                .bill(bill)
                .amount(req.getAmount())
                .paymentMethod(req.getPaymentMethod())
                .build();

        DummyPaymentGateway.PaymentResult result =
                paymentGateway.process(req.getAmount(), req.getPaymentMethod().name(), bill.getPatientId());

        if (result.success()) {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setTransactionRef(result.transactionId());
            payment.setPaidAt(Instant.now());

            bill.setPaidAmount(bill.getPaidAmount().add(req.getAmount()));
            BigDecimal newRemaining = bill.getTotalAmount().subtract(bill.getPaidAmount());
            bill.setStatus(newRemaining.compareTo(BigDecimal.ZERO) == 0
                    ? BillStatus.PAID : BillStatus.PARTIAL);
            billRepository.save(bill);

            Payment saved = paymentRepository.save(payment);
            eventPublisher.publishPaymentSuccess(new BillingEvents.PaymentSuccessEvent(
                    saved.getId(), bill.getId(), bill.getPatientId(), req.getAmount(), result.transactionId()));
            return toResponse(saved);
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            Payment saved = paymentRepository.save(payment);

            eventPublisher.publishPaymentFailed(new BillingEvents.PaymentFailedEvent(
                    saved.getId(), bill.getId(), bill.getPatientId(), req.getAmount(), result.failureReason()));
            return toResponse(saved);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id) {
        return toResponse(paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> getPaymentsForBill(Long billId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.from(paymentRepository.findByBillId(billId, pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> getPaymentsForPatient(Long patientId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.from(paymentRepository.findByBill_PatientId(patientId, pageable).map(this::toResponse));
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .uuid(p.getUuid())
                .billId(p.getBill().getId())
                .patientId(p.getBill().getPatientId())
                .amount(p.getAmount())
                .paymentStatus(p.getPaymentStatus())
                .paymentMethod(p.getPaymentMethod())
                .transactionRef(p.getTransactionRef())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
