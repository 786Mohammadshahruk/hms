package com.hms.appointment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hms.appointment.dto.request.BlockedSlotRequest;
import com.hms.appointment.dto.request.DoctorScheduleRequest;
import com.hms.appointment.dto.response.SlotResponse;
import com.hms.appointment.entity.DoctorSchedule;
import com.hms.appointment.enums.ScheduleDay;
import com.hms.appointment.service.DoctorScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DoctorScheduleController Tests")
class DoctorScheduleControllerTest {

    @Mock DoctorScheduleService scheduleService;
    @InjectMocks DoctorScheduleController scheduleController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(scheduleController).build();
    }

    // ── POST /schedules/doctor/{doctorId} ──────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/schedules/doctor/{doctorId}")
    class SetScheduleTests {

        @Test
        @DisplayName("Should return 200 when schedule is set successfully")
        void setSchedule_success() throws Exception {
            DoctorScheduleRequest req = new DoctorScheduleRequest();
            req.setDayOfWeek(ScheduleDay.MONDAY);
            req.setStartTime(LocalTime.of(9, 0));
            req.setEndTime(LocalTime.of(17, 0));
            req.setSlotDuration(30);

            DoctorSchedule saved = DoctorSchedule.builder()
                .id(1L).doctorId(10L).dayOfWeek(ScheduleDay.MONDAY)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0))
                .slotDuration(30).active(true).build();

            when(scheduleService.setSchedule(eq(10L), any())).thenReturn(saved);

            mockMvc.perform(post("/api/v1/schedules/doctor/10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.doctorId").value(10))
                .andExpect(jsonPath("$.data.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.slotDuration").value(30));

            verify(scheduleService).setSchedule(eq(10L), any(DoctorScheduleRequest.class));
        }
    }

    // ── GET /schedules/doctor/{doctorId} ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/schedules/doctor/{doctorId}")
    class GetSchedulesTests {

        @Test
        @DisplayName("Should return 200 with list of active schedules")
        void getSchedules_success() throws Exception {
            DoctorSchedule monday = DoctorSchedule.builder()
                .id(1L).doctorId(10L).dayOfWeek(ScheduleDay.MONDAY)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0))
                .slotDuration(30).active(true).build();

            DoctorSchedule wednesday = DoctorSchedule.builder()
                .id(2L).doctorId(10L).dayOfWeek(ScheduleDay.WEDNESDAY)
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(14, 0))
                .slotDuration(30).active(true).build();

            when(scheduleService.getDoctorSchedules(10L)).thenReturn(List.of(monday, wednesday));

            mockMvc.perform(get("/api/v1/schedules/doctor/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data[1].dayOfWeek").value("WEDNESDAY"));
        }

        @Test
        @DisplayName("Should return 200 with empty list when no schedules exist")
        void getSchedules_empty() throws Exception {
            when(scheduleService.getDoctorSchedules(10L)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/schedules/doctor/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── GET /schedules/doctor/{doctorId}/slots ─────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/schedules/doctor/{doctorId}/slots")
    class GetSlotsTests {

        @Test
        @DisplayName("Should return 200 with available slots for the given date")
        void getSlots_success() throws Exception {
            LocalDate date = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(1);

            List<SlotResponse> slots = List.of(
                SlotResponse.builder().startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(9, 30)).available(true).build(),
                SlotResponse.builder().startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).available(false).build(),
                SlotResponse.builder().startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(10, 30)).available(true).build()
            );

            when(scheduleService.getAvailableSlots(10L, date)).thenReturn(slots);

            mockMvc.perform(get("/api/v1/schedules/doctor/10/slots")
                    .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].available").value(true))
                .andExpect(jsonPath("$.data[1].available").value(false));
        }

        @Test
        @DisplayName("Should return 200 with empty list when no schedule on that day")
        void getSlots_noSchedule_returnsEmpty() throws Exception {
            LocalDate sunday = LocalDate.now().with(java.time.DayOfWeek.SUNDAY).plusWeeks(1);

            when(scheduleService.getAvailableSlots(10L, sunday)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/schedules/doctor/10/slots")
                    .param("date", sunday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── POST /schedules/doctor/{doctorId}/block ────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/schedules/doctor/{doctorId}/block")
    class BlockSlotTests {

        @Test
        @DisplayName("Should return 200 when slot is blocked successfully")
        void blockSlot_success() throws Exception {
            BlockedSlotRequest req = new BlockedSlotRequest();
            req.setBlockedDate(LocalDate.now().plusDays(3));
            req.setStartTime(LocalTime.of(14, 0));
            req.setEndTime(LocalTime.of(14, 30));
            req.setReason("Conference");

            doNothing().when(scheduleService).blockSlot(eq(10L), any());

            mockMvc.perform(post("/api/v1/schedules/doctor/10/block")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Slot blocked successfully"));

            verify(scheduleService).blockSlot(eq(10L), any(BlockedSlotRequest.class));
        }
    }
}
