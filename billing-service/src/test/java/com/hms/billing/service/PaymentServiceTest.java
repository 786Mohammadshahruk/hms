package com.hms.billing.service;

import com.hms.billing.dto.request.ProcessPaymentRequest;
import com.hms.billing.dto.response.PaymentResponse;
import com.hms.billing.entity.Bill;
import com.hms.billing.entity.BillItem;
import com.hms.billing.entity.Payment;
import com.hms.billing.enums.BillStatus;
import com.hms.billing.enums.PaymentMethod;
import com.hms.billing.enums.PaymentStatus;
import com.hms.billing.exception.BadRequestException;
import com.hms.billing.exception.ResourceNotFoundException;
import com.hms.billing.gateway.DummyPaymentGateway;
import com.hms.billing.kafka.BillingEventPublisher;
import com.hms.billing.repository.BillRepository;
import com.hms.billing.repository.PaymentRepository;
import com.hms.billing.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Tests")
class PaymentServiceTest {

    @Mock PaymentRepository     paymentRepository;
    @Mock BillRepository        billRepository;
    @Mock DummyPaymentGateway   paymentGateway;
    @Mock BillingEventPublisher eventPublisher;

    @InjectMocks PaymentServiceImpl paymentService;

    private Bill pendingBill;

    @BeforeEach
    void setUp() {
        pendingBill = new Bill();
        pendingBill.setId(1L);
        pendingBill.setPatientId(10L);
        pendingBill.setTotalAmount(new BigDecimal("1000.00"));
        pendingBill.setPaidAmount(BigDecimal.ZERO);
        pendingBill.setStatus(BillStatus.PENDING);
        pendingBill.setItems(new ArrayList<>());
        pendingBill.setPayments(new ArrayList<>());

        BillItem item = new BillItem();
        item.setId(1L);
        item.setDescription("Consultation");
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setQuantity(1);
        item.setTotalPrice(new BigDecimal("1000.00"));
        item.setBill(pendingBill);
        pendingBill.getItems().add(item);
    }

    // ── processPayment ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("processPayment()")
    class ProcessPaymentTests {

        @Test
        @DisplayName("Should mark bill as PAID on full successful payment")
        void processPayment_fullPayment_success() {
            ProcessPaymentRequest req = new ProcessPaymentRequest(
                1L, new BigDecimal("1000.00"), PaymentMethod.CASH);

            Payment savedPayment = buildPayment(1L, PaymentStatus.SUCCESS, "TXN-001");

            when(billRepository.findById(1L)).thenReturn(Optional.of(pendingBill));
            when(paymentGateway.process(any(), anyString(), anyLong()))
                .thenReturn(new DummyPaymentGateway.PaymentResult(true, "TXN-001", null));
            when(billRepository.save(any())).thenReturn(pendingBill);
            when(paymentRepository.save(any())).thenReturn(savedPayment);

            PaymentResponse response = paymentService.processPayment(10L, req);

            assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(response.getTransactionRef()).isEqualTo("TXN-001");
            verify(billRepository).save(argThat(b -> b.getStatus() == BillStatus.PAID));
            verify(eventPublisher).publishPaymentSuccess(any());
        }

        @Test
        @DisplayName("Should mark bill as PARTIAL on partial successful payment")
        void processPayment_partialPayment_success() {
            ProcessPaymentRequest req = new ProcessPaymentRequest(
                1L, new BigDecimal("500.00"), PaymentMethod.CARD);

            Payment savedPayment = buildPayment(1L, PaymentStatus.SUCCESS, "TXN-002");

            when(billRepository.findById(1L)).thenReturn(Optional.of(pendingBill));
            when(paymentGateway.process(any(), anyString(), anyLong()))
                .thenReturn(new DummyPaymentGateway.PaymentResult(true, "TXN-002", null));
            when(billRepository.save(any())).thenReturn(pendingBill);
            when(paymentRepository.save(any())).thenReturn(savedPayment);

            paymentService.processPayment(10L, req);

            verify(billRepository).save(argThat(b -> b.getStatus() == BillStatus.PARTIAL));
        }

        @Test
        @DisplayName("Should save FAILED payment when gateway declines")
        void processPayment_gatewayFailed_savesFailedPayment() {
            ProcessPaymentRequest req = new ProcessPaymentRequest(
                1L, new BigDecimal("1000.00"), PaymentMethod.ONLINE);

            Payment failedPayment = buildPayment(2L, PaymentStatus.FAILED, null);

            when(billRepository.findById(1L)).thenReturn(Optional.of(pendingBill));
            when(paymentGateway.process(any(), anyString(), anyLong()))
                .thenReturn(new DummyPaymentGateway.PaymentResult(false, null, "Insufficient funds"));
            when(paymentRepository.save(any())).thenReturn(failedPayment);

            PaymentResponse response = paymentService.processPayment(10L, req);

            assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(eventPublisher).publishPaymentFailed(any());
            verify(billRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when bill is already PAID")
        void processPayment_alreadyPaid_throws() {
            pendingBill.setStatus(BillStatus.PAID);
            ProcessPaymentRequest req = new ProcessPaymentRequest(
                1L, new BigDecimal("100.00"), PaymentMethod.CASH);

            when(billRepository.findById(1L)).thenReturn(Optional.of(pendingBill));

            assertThatThrownBy(() -> paymentService.processPayment(10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already fully paid");
        }

        @Test
        @DisplayName("Should throw BadRequestException when bill is CANCELLED")
        void processPayment_cancelledBill_throws() {
            pendingBill.setStatus(BillStatus.CANCELLED);
            ProcessPaymentRequest req = new ProcessPaymentRequest(
                1L, new BigDecimal("100.00"), PaymentMethod.CASH);

            when(billRepository.findById(1L)).thenReturn(Optional.of(pendingBill));

            assertThatThrownBy(() -> paymentService.processPayment(10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cancelled bill");
        }

        @Test
        @DisplayName("Should throw BadRequestException when amount exceeds remaining balance")
        void processPayment_amountExceedsBalance_throws() {
            pendingBill.setPaidAmount(new BigDecimal("800.00")); // 200 remaining
            ProcessPaymentRequest req = new ProcessPaymentRequest(
                1L, new BigDecimal("500.00"), PaymentMethod.UPI);

            when(billRepository.findById(1L)).thenReturn(Optional.of(pendingBill));

            assertThatThrownBy(() -> paymentService.processPayment(10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("remaining balance");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when bill does not exist")
        void processPayment_billNotFound_throws() {
            ProcessPaymentRequest req = new ProcessPaymentRequest(
                999L, new BigDecimal("100.00"), PaymentMethod.CASH);

            when(billRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.processPayment(10L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
        }
    }

    // ── getPayment ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPayment()")
    class GetPaymentTests {

        @Test
        @DisplayName("Should return payment when found")
        void getPayment_success() {
            Payment payment = buildPayment(1L, PaymentStatus.SUCCESS, "TXN-003");
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            PaymentResponse response = paymentService.getPayment(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when payment not found")
        void getPayment_notFound_throws() {
            when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPayment(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Payment buildPayment(Long id, PaymentStatus status, String txnRef) {
        Payment p = new Payment();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setBill(pendingBill);
        p.setAmount(new BigDecimal("1000.00"));
        p.setPaymentMethod(PaymentMethod.CASH);
        p.setPaymentStatus(status);
        p.setTransactionRef(txnRef);
        if (status == PaymentStatus.SUCCESS) p.setPaidAt(Instant.now());
        return p;
    }
}
