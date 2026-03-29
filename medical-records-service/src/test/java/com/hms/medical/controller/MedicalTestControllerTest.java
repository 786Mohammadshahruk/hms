package com.hms.medical.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hms.medical.dto.request.OrderTestRequest;
import com.hms.medical.dto.request.UpdateTestResultRequest;
import com.hms.medical.dto.response.MedicalTestResponse;
import com.hms.medical.dto.response.PagedResponse;
import com.hms.medical.enums.TestStatus;
import com.hms.medical.exception.BadRequestException;
import com.hms.medical.exception.ResourceNotFoundException;
import com.hms.medical.security.UserPrincipal;
import com.hms.medical.service.MedicalTestService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicalTestController Tests")
class MedicalTestControllerTest {

    @Mock MedicalTestService medicalTestService;
    @InjectMocks MedicalTestController medicalTestController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    private UserPrincipal doctorPrincipal;
    private UserPrincipal patientPrincipal;
    private MedicalTestResponse sampleResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
            .standaloneSetup(medicalTestController)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        doctorPrincipal  = new UserPrincipal(20L, "doctor@example.com", "DOCTOR");
        patientPrincipal = new UserPrincipal(10L, "patient@example.com", "PATIENT");

        sampleResponse = MedicalTestResponse.builder()
            .id(1L).uuid(UUID.randomUUID())
            .patientId(10L).doctorId(20L)
            .testName("Complete Blood Count").testType("HAEMATOLOGY")
            .status(TestStatus.ORDERED)
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

    // ── POST /tests ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/tests")
    class OrderTestTests {

        @Test
        @DisplayName("Should return 201 when test is ordered successfully")
        void orderTest_success_returns201() throws Exception {
            authenticateAs(doctorPrincipal);

            OrderTestRequest req = new OrderTestRequest(10L, null, "Complete Blood Count", "HAEMATOLOGY");

            when(medicalTestService.orderTest(eq(20L), any())).thenReturn(sampleResponse);

            mockMvc.perform(post("/api/v1/tests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Test ordered"))
                .andExpect(jsonPath("$.data.testName").value("Complete Blood Count"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

            verify(medicalTestService).orderTest(eq(20L), any(OrderTestRequest.class));
        }
    }

    // ── GET /tests/{id} ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/tests/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("Should return 200 with test data")
        void getById_success() throws Exception {
            when(medicalTestService.getTest(1L)).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/v1/tests/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.testName").value("Complete Blood Count"))
                .andExpect(jsonPath("$.data.patientId").value(10));
        }

        @Test
        @DisplayName("Should return 404 when test is not found")
        void getById_notFound_returns404() throws Exception {
            when(medicalTestService.getTest(999L))
                .thenThrow(new ResourceNotFoundException("Medical test not found: 999"));

            mockMvc.perform(get("/api/v1/tests/999"))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /tests/my ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/tests/my")
    class GetMyTestsTests {

        @Test
        @DisplayName("Should return 200 with patient's own tests")
        void getMyTests_success() throws Exception {
            authenticateAs(patientPrincipal);

            PagedResponse<MedicalTestResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(sampleResponse)));

            when(medicalTestService.getTestsForPatient(10L, 0, 10)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/tests/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].patientId").value(10));
        }
    }

    // ── GET /tests/doctor/my ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/tests/doctor/my")
    class GetDoctorTestsTests {

        @Test
        @DisplayName("Should return 200 with tests ordered by the doctor")
        void getDoctorTests_success() throws Exception {
            authenticateAs(doctorPrincipal);

            PagedResponse<MedicalTestResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(sampleResponse)));

            when(medicalTestService.getTestsByDoctor(20L, 0, 10)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/tests/doctor/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].doctorId").value(20));
        }
    }

    // ── PUT /tests/{id}/result ─────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/tests/{id}/result")
    class UploadResultTests {

        @Test
        @DisplayName("Should return 200 when result is uploaded successfully")
        void uploadResult_success() throws Exception {
            UpdateTestResultRequest req = new UpdateTestResultRequest(
                "Normal — 5.2 million RBC/µL", false, "Within reference range");

            MedicalTestResponse completed = MedicalTestResponse.builder()
                .id(1L).patientId(10L).doctorId(20L)
                .testName("Complete Blood Count").testType("HAEMATOLOGY")
                .status(TestStatus.COMPLETED)
                .resultValue("Normal — 5.2 million RBC/µL")
                .isAbnormal(false)
                .build();

            when(medicalTestService.updateResult(eq(1L), any())).thenReturn(completed);

            mockMvc.perform(put("/api/v1/tests/1/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Result uploaded"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.isAbnormal").value(false));
        }

        @Test
        @DisplayName("Should return 400 when result already submitted")
        void uploadResult_alreadySubmitted_returns400() throws Exception {
            UpdateTestResultRequest req = new UpdateTestResultRequest("Some value", false, null);

            when(medicalTestService.updateResult(anyLong(), any()))
                .thenThrow(new BadRequestException("Test result already submitted"));

            mockMvc.perform(put("/api/v1/tests/1/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }
}
