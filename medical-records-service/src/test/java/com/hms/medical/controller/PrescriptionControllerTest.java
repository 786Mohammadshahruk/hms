package com.hms.medical.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hms.medical.dto.request.AddMedicineRequest;
import com.hms.medical.dto.request.CreatePrescriptionRequest;
import com.hms.medical.dto.response.PagedResponse;
import com.hms.medical.dto.response.PrescriptionResponse;
import com.hms.medical.enums.PrescriptionStatus;
import com.hms.medical.exception.BadRequestException;
import com.hms.medical.exception.ResourceNotFoundException;
import com.hms.medical.security.UserPrincipal;
import com.hms.medical.service.PrescriptionService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrescriptionController Tests")
class PrescriptionControllerTest {

    @Mock PrescriptionService prescriptionService;
    @InjectMocks PrescriptionController prescriptionController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    private UserPrincipal doctorPrincipal;
    private UserPrincipal patientPrincipal;
    private PrescriptionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
            .standaloneSetup(prescriptionController)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        doctorPrincipal  = new UserPrincipal(20L, "doctor@example.com", "DOCTOR");
        patientPrincipal = new UserPrincipal(10L, "patient@example.com", "PATIENT");

        sampleResponse = PrescriptionResponse.builder()
            .id(1L).patientId(10L).doctorId(20L)
            .diagnosis("Fever").status(PrescriptionStatus.ACTIVE)
            .validUntil(LocalDate.now().plusDays(30))
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

    // ── POST /prescriptions ────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/prescriptions")
    class CreateTests {

        @Test
        @DisplayName("Should return 201 when prescription is created successfully")
        void create_success_returns201() throws Exception {
            authenticateAs(doctorPrincipal);

            CreatePrescriptionRequest req = CreatePrescriptionRequest.builder()
                .patientId(10L).diagnosis("Fever")
                .medicines(List.of(new AddMedicineRequest("Paracetamol", "500mg", "3x daily", 5, null, 15)))
                .build();

            when(prescriptionService.createPrescription(eq(20L), any())).thenReturn(sampleResponse);

            mockMvc.perform(post("/api/v1/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Prescription created"))
                .andExpect(jsonPath("$.data.diagnosis").value("Fever"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            verify(prescriptionService).createPrescription(eq(20L), any(CreatePrescriptionRequest.class));
        }
    }

    // ── GET /prescriptions/{id} ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/prescriptions/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("Should return 200 with prescription data")
        void getById_success() throws Exception {
            when(prescriptionService.getPrescription(1L)).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/v1/prescriptions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.diagnosis").value("Fever"))
                .andExpect(jsonPath("$.data.patientId").value(10));
        }

        @Test
        @DisplayName("Should return 404 when prescription does not exist")
        void getById_notFound_returns404() throws Exception {
            when(prescriptionService.getPrescription(999L))
                .thenThrow(new ResourceNotFoundException("Prescription not found: 999"));

            mockMvc.perform(get("/api/v1/prescriptions/999"))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /prescriptions/my ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/prescriptions/my")
    class GetMyPrescriptionsTests {

        @Test
        @DisplayName("Should return 200 with patient's own prescriptions")
        void getMyPrescriptions_success() throws Exception {
            authenticateAs(patientPrincipal);

            PagedResponse<PrescriptionResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(sampleResponse)));

            when(prescriptionService.getPrescriptionsForPatient(10L, 0, 10)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/prescriptions/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].patientId").value(10));
        }
    }

    // ── GET /prescriptions/doctor/my ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/prescriptions/doctor/my")
    class GetDoctorPrescriptionsTests {

        @Test
        @DisplayName("Should return 200 with doctor's written prescriptions")
        void getDoctorPrescriptions_success() throws Exception {
            authenticateAs(doctorPrincipal);

            PagedResponse<PrescriptionResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(sampleResponse)));

            when(prescriptionService.getPrescriptionsByDoctor(20L, 0, 10)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/prescriptions/doctor/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].doctorId").value(20));
        }
    }

    // ── PATCH /prescriptions/{id}/cancel ──────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/prescriptions/{id}/cancel")
    class CancelTests {

        @Test
        @DisplayName("Should return 200 when prescription is cancelled")
        void cancel_success() throws Exception {
            authenticateAs(doctorPrincipal);

            PrescriptionResponse cancelled = PrescriptionResponse.builder()
                .id(1L).status(PrescriptionStatus.CANCELLED).build();

            when(prescriptionService.cancelPrescription(1L, 20L)).thenReturn(cancelled);

            mockMvc.perform(patch("/api/v1/prescriptions/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("Should return 400 when doctor is not authorized to cancel")
        void cancel_unauthorized_returns400() throws Exception {
            authenticateAs(doctorPrincipal);

            when(prescriptionService.cancelPrescription(1L, 20L))
                .thenThrow(new BadRequestException("You are not authorized to cancel this prescription"));

            mockMvc.perform(patch("/api/v1/prescriptions/1/cancel"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when prescription is already cancelled")
        void cancel_alreadyCancelled_returns400() throws Exception {
            authenticateAs(doctorPrincipal);

            when(prescriptionService.cancelPrescription(1L, 20L))
                .thenThrow(new BadRequestException("Prescription is already cancelled"));

            mockMvc.perform(patch("/api/v1/prescriptions/1/cancel"))
                .andExpect(status().isBadRequest());
        }
    }
}
