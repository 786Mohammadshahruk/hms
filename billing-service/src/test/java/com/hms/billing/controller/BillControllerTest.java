package com.hms.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hms.billing.dto.request.BillItemRequest;
import com.hms.billing.dto.request.CreateBillRequest;
import com.hms.billing.dto.response.BillResponse;
import com.hms.billing.dto.response.PagedResponse;
import com.hms.billing.enums.BillStatus;
import com.hms.billing.exception.BadRequestException;
import com.hms.billing.exception.ResourceNotFoundException;
import com.hms.billing.security.UserPrincipal;
import com.hms.billing.service.BillService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BillController Tests")
class BillControllerTest {

    @Mock BillService billService;
    @InjectMocks BillController billController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    private UserPrincipal patientPrincipal;
    private BillResponse sampleBill;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
            .standaloneSetup(billController)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        patientPrincipal = new UserPrincipal(10L, "patient@example.com", "PATIENT");

        sampleBill = BillResponse.builder()
            .id(1L).uuid(UUID.randomUUID())
            .patientId(10L)
            .totalAmount(new BigDecimal("500.00"))
            .paidAmount(BigDecimal.ZERO)
            .status(BillStatus.PENDING)
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

    // ── POST /bills ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/bills")
    class CreateBillTests {

        @Test
        @DisplayName("Should return 201 when bill is created successfully")
        void create_success_returns201() throws Exception {
            CreateBillRequest req = new CreateBillRequest(
                10L, null, "Consultation fee", null,
                List.of(new BillItemRequest("Consultation", new BigDecimal("500.00"), 1))
            );

            when(billService.createBill(any())).thenReturn(sampleBill);

            mockMvc.perform(post("/api/v1/bills")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Bill created"))
                .andExpect(jsonPath("$.data.patientId").value(10))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(500.00));

            verify(billService).createBill(any(CreateBillRequest.class));
        }
    }

    // ── GET /bills/{id} ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/bills/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("Should return 200 with bill data")
        void getById_success() throws Exception {
            when(billService.getBill(1L)).thenReturn(sampleBill);

            mockMvc.perform(get("/api/v1/bills/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(500.0));
        }

        @Test
        @DisplayName("Should return 404 when bill does not exist")
        void getById_notFound_returns404() throws Exception {
            when(billService.getBill(999L))
                .thenThrow(new ResourceNotFoundException("Bill not found: 999"));

            mockMvc.perform(get("/api/v1/bills/999"))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /bills/my ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/bills/my")
    class GetMyBillsTests {

        @Test
        @DisplayName("Should return 200 with patient's own bills")
        void getMyBills_success() throws Exception {
            authenticateAs(patientPrincipal);

            PagedResponse<BillResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(sampleBill)));

            when(billService.getBillsForPatient(10L, 0, 10)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/bills/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].patientId").value(10));
        }
    }

    // ── GET /bills/patient/{patientId} ─────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/bills/patient/{patientId}")
    class GetForPatientTests {

        @Test
        @DisplayName("Should return 200 with bills for the specified patient")
        void getForPatient_success() throws Exception {
            PagedResponse<BillResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(sampleBill)));

            when(billService.getBillsForPatient(10L, 0, 10)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/bills/patient/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].patientId").value(10));
        }
    }

    // ── PATCH /bills/{id}/cancel ───────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/bills/{id}/cancel")
    class CancelBillTests {

        @Test
        @DisplayName("Should return 200 when bill is cancelled")
        void cancel_success() throws Exception {
            BillResponse cancelled = BillResponse.builder()
                .id(1L).patientId(10L).status(BillStatus.CANCELLED)
                .totalAmount(new BigDecimal("500.00")).paidAmount(BigDecimal.ZERO).build();

            when(billService.cancelBill(1L)).thenReturn(cancelled);

            mockMvc.perform(patch("/api/v1/bills/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("Should return 400 when bill is already paid")
        void cancel_alreadyPaid_returns400() throws Exception {
            when(billService.cancelBill(1L))
                .thenThrow(new BadRequestException("Cannot cancel a paid bill"));

            mockMvc.perform(patch("/api/v1/bills/1/cancel"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 404 when bill does not exist")
        void cancel_notFound_returns404() throws Exception {
            when(billService.cancelBill(999L))
                .thenThrow(new ResourceNotFoundException("Bill not found: 999"));

            mockMvc.perform(patch("/api/v1/bills/999/cancel"))
                .andExpect(status().isNotFound());
        }
    }
}
