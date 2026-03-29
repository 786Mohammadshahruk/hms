package com.hms.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hms.billing.dto.request.ProcessPaymentRequest;
import com.hms.billing.dto.response.PagedResponse;
import com.hms.billing.dto.response.PaymentResponse;
import com.hms.billing.enums.PaymentMethod;
import com.hms.billing.enums.PaymentStatus;
import com.hms.billing.exception.BadRequestException;
import com.hms.billing.exception.ResourceNotFoundException;
import com.hms.billing.security.UserPrincipal;
import com.hms.billing.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController Tests")
class PaymentControllerTest {

    @Mock PaymentService paymentService;
    @InjectMocks PaymentController paymentController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    private UserPrincipal patientPrincipal;
    private PaymentResponse successPayment;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
            .standaloneSetup(paymentController)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        patientPrincipal = new UserPrincipal(10L, "patient@example.com", "PATIENT");

        successPayment = PaymentResponse.builder()
            .id(1L).uuid(UUID.randomUUID())
            .billId(5L).patientId(10L)
            .amount(new BigDecimal("500.00"))
            .paymentMethod(PaymentMethod.CASH)
            .paymentStatus(PaymentStatus.SUCCESS)
            .transactionRef("TXN-001")
            .paidAt(Instant.now())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    // ── POST /payments ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/payments")
    class ProcessPaymentTests {

        @Test
        @DisplayName("Should return 201 with SUCCESS payment when gateway approves")
        void processPayment_success_returns201() throws Exception {
            authenticateAs(patientPrincipal);

            ProcessPaymentRequest req = new ProcessPaymentRequest(
                5L, new BigDecimal("500.00"), PaymentMethod.CASH);

            when(paymentService.processPayment(eq(10L), any())).thenReturn(successPayment);

            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Payment processed"))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.transactionRef").value("TXN-001"))
                .andExpect(jsonPath("$.data.amount").value(500.00));

            verify(paymentService).processPayment(eq(10L), any(ProcessPaymentRequest.class));
        }

        @Test
        @DisplayName("Should return 201 with FAILED payment when gateway declines")
        void processPayment_gatewayDeclined_returns201WithFailedStatus() throws Exception {
            authenticateAs(patientPrincipal);

            ProcessPaymentRequest req = new ProcessPaymentRequest(
                5L, new BigDecimal("500.00"), PaymentMethod.CARD);

            PaymentResponse failed = PaymentResponse.builder()
                .id(2L).billId(5L).patientId(10L)
                .amount(new BigDecimal("500.00"))
                .paymentMethod(PaymentMethod.CARD)
                .paymentStatus(PaymentStatus.FAILED)
                .build();

            when(paymentService.processPayment(eq(10L), any())).thenReturn(failed);

            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"));
        }

        @Test
        @DisplayName("Should return 400 when bill is already paid")
        void processPayment_alreadyPaid_returns400() throws Exception {
            authenticateAs(patientPrincipal);

            ProcessPaymentRequest req = new ProcessPaymentRequest(
                5L, new BigDecimal("500.00"), PaymentMethod.UPI);

            when(paymentService.processPayment(anyLong(), any()))
                .thenThrow(new BadRequestException("Bill is already fully paid"));

            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 404 when bill does not exist")
        void processPayment_billNotFound_returns404() throws Exception {
            authenticateAs(patientPrincipal);

            ProcessPaymentRequest req = new ProcessPaymentRequest(
                999L, new BigDecimal("100.00"), PaymentMethod.CASH);

            when(paymentService.processPayment(anyLong(), any()))
                .thenThrow(new ResourceNotFoundException("Bill not found: 999"));

            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /payments/{id} ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/payments/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("Should return 200 with payment data")
        void getById_success() throws Exception {
            when(paymentService.getPayment(1L)).thenReturn(successPayment);

            mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.transactionRef").value("TXN-001"));
        }

        @Test
        @DisplayName("Should return 404 when payment does not exist")
        void getById_notFound_returns404() throws Exception {
            when(paymentService.getPayment(999L))
                .thenThrow(new ResourceNotFoundException("Payment not found: 999"));

            mockMvc.perform(get("/api/v1/payments/999"))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /payments/bill/{billId} ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/payments/bill/{billId}")
    class GetForBillTests {

        @Test
        @DisplayName("Should return 200 with payments for the bill")
        void getForBill_success() throws Exception {
            PagedResponse<PaymentResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(successPayment)));

            when(paymentService.getPaymentsForBill(5L, 0, 10)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/payments/bill/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].billId").value(5));
        }
    }

    // ── GET /payments/my ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/payments/my")
    class GetMyPaymentsTests {

        @Test
        @DisplayName("Should return 200 with patient's own payment history")
        void getMyPayments_success() throws Exception {
            authenticateAs(patientPrincipal);

            PagedResponse<PaymentResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(successPayment)));

            when(paymentService.getPaymentsForPatient(10L, 0, 10)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/payments/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].patientId").value(10));
        }
    }
}
