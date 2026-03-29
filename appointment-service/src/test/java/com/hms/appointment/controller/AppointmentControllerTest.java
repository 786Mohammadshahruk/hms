package com.hms.appointment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hms.appointment.dto.request.BookAppointmentRequest;
import com.hms.appointment.dto.request.CancelAppointmentRequest;
import com.hms.appointment.dto.response.AppointmentResponse;
import com.hms.appointment.dto.response.PagedResponse;
import com.hms.appointment.enums.AppointmentStatus;
import com.hms.appointment.enums.AppointmentType;
import com.hms.appointment.exception.BadRequestException;
import com.hms.appointment.exception.ConflictException;
import com.hms.appointment.exception.ResourceNotFoundException;
import com.hms.appointment.security.UserPrincipal;
import com.hms.appointment.service.AppointmentService;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentController Tests")
class AppointmentControllerTest {

    @Mock AppointmentService appointmentService;
    @InjectMocks AppointmentController appointmentController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    private UserPrincipal patientPrincipal;
    private AppointmentResponse sampleResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
            .standaloneSetup(appointmentController)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        patientPrincipal = new UserPrincipal(1L, "patient@example.com", "PATIENT");

        sampleResponse = AppointmentResponse.builder()
            .id(1L).uuid(UUID.randomUUID())
            .patientId(1L).doctorId(10L)
            .appointmentDate(LocalDate.now().plusDays(7))
            .startTime(LocalTime.of(10, 0))
            .endTime(LocalTime.of(10, 30))
            .status(AppointmentStatus.SCHEDULED)
            .type(AppointmentType.CONSULTATION)
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

    // ── POST /appointments ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/appointments")
    class BookTests {

        @Test
        @DisplayName("Should return 201 when appointment is booked successfully")
        void book_success_returns201() throws Exception {
            authenticateAs(patientPrincipal);

            BookAppointmentRequest req = new BookAppointmentRequest();
            req.setDoctorId(10L);
            req.setAppointmentDate(LocalDate.now().plusDays(7));
            req.setStartTime(LocalTime.of(10, 0));
            req.setType(AppointmentType.CONSULTATION);

            when(appointmentService.bookAppointment(eq(1L), any())).thenReturn(sampleResponse);

            mockMvc.perform(post("/api/v1/appointments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.doctorId").value(10));

            verify(appointmentService).bookAppointment(eq(1L), any(BookAppointmentRequest.class));
        }

        @Test
        @DisplayName("Should return 409 when slot is already booked")
        void book_conflict_returns409() throws Exception {
            authenticateAs(patientPrincipal);

            BookAppointmentRequest req = new BookAppointmentRequest();
            req.setDoctorId(10L);
            req.setAppointmentDate(LocalDate.now().plusDays(7));
            req.setStartTime(LocalTime.of(10, 0));
            req.setType(AppointmentType.CONSULTATION);

            when(appointmentService.bookAppointment(anyLong(), any()))
                .thenThrow(new ConflictException("This time slot is already booked"));

            mockMvc.perform(post("/api/v1/appointments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should return 400 when no schedule exists for that day")
        void book_noSchedule_returns400() throws Exception {
            authenticateAs(patientPrincipal);

            BookAppointmentRequest req = new BookAppointmentRequest();
            req.setDoctorId(10L);
            req.setAppointmentDate(LocalDate.now().plusDays(7));
            req.setStartTime(LocalTime.of(10, 0));
            req.setType(AppointmentType.CONSULTATION);

            when(appointmentService.bookAppointment(anyLong(), any()))
                .thenThrow(new BadRequestException("Doctor has no schedule for this day"));

            mockMvc.perform(post("/api/v1/appointments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /{id}/cancel ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/appointments/{id}/cancel")
    class CancelTests {

        @Test
        @DisplayName("Should return 200 when appointment is cancelled successfully")
        void cancel_success_returns200() throws Exception {
            authenticateAs(patientPrincipal);

            CancelAppointmentRequest req = new CancelAppointmentRequest();
            req.setReason("Personal emergency");

            AppointmentResponse cancelled = AppointmentResponse.builder()
                .id(1L).status(AppointmentStatus.CANCELLED).build();

            when(appointmentService.cancelAppointment(eq(1L), eq(1L), any()))
                .thenReturn(cancelled);

            mockMvc.perform(put("/api/v1/appointments/1/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("Should return 400 when patient tries to cancel someone else's appointment")
        void cancel_wrongPatient_returns400() throws Exception {
            authenticateAs(patientPrincipal);

            CancelAppointmentRequest req = new CancelAppointmentRequest();
            req.setReason("Test");

            when(appointmentService.cancelAppointment(anyLong(), anyLong(), any()))
                .thenThrow(new BadRequestException("You can only cancel your own appointments"));

            mockMvc.perform(put("/api/v1/appointments/1/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ── GET /{id} ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/appointments/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("Should return 200 with appointment data")
        void getById_success() throws Exception {
            when(appointmentService.getAppointmentById(1L)).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/v1/appointments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        }

        @Test
        @DisplayName("Should return 404 when appointment does not exist")
        void getById_notFound_returns404() throws Exception {
            when(appointmentService.getAppointmentById(999L))
                .thenThrow(new ResourceNotFoundException("Appointment", "id", 999L));

            mockMvc.perform(get("/api/v1/appointments/999"))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /my ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/appointments/my")
    class GetMyAppointmentsTests {

        @Test
        @DisplayName("Should return 200 with patient's appointments")
        void getMyAppointments_success() throws Exception {
            authenticateAs(patientPrincipal);

            PagedResponse<AppointmentResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(sampleResponse)));

            when(appointmentService.getPatientAppointments(eq(1L), any(), any()))
                .thenReturn(paged);

            mockMvc.perform(get("/api/v1/appointments/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].patientId").value(1));
        }
    }

    // ── PATCH /{id}/status ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/appointments/{id}/status")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should return 200 when status is updated")
        void updateStatus_success() throws Exception {
            AppointmentResponse completed = AppointmentResponse.builder()
                .id(1L).status(AppointmentStatus.COMPLETED).build();

            when(appointmentService.updateStatus(1L, AppointmentStatus.COMPLETED))
                .thenReturn(completed);

            mockMvc.perform(patch("/api/v1/appointments/1/status")
                    .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }
    }
}
