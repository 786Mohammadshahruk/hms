package com.hms.appointment.service;

import com.hms.appointment.dto.request.BlockedSlotRequest;
import com.hms.appointment.dto.request.DoctorScheduleRequest;
import com.hms.appointment.dto.response.SlotResponse;
import com.hms.appointment.entity.Appointment;
import com.hms.appointment.entity.BlockedSlot;
import com.hms.appointment.entity.DoctorSchedule;
import com.hms.appointment.enums.AppointmentStatus;
import com.hms.appointment.enums.AppointmentType;
import com.hms.appointment.enums.ScheduleDay;
import com.hms.appointment.repository.AppointmentRepository;
import com.hms.appointment.repository.BlockedSlotRepository;
import com.hms.appointment.repository.DoctorScheduleRepository;
import com.hms.appointment.service.impl.DoctorScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DoctorScheduleService Tests")
class DoctorScheduleServiceTest {

    @Mock DoctorScheduleRepository doctorScheduleRepository;
    @Mock AppointmentRepository    appointmentRepository;
    @Mock BlockedSlotRepository    blockedSlotRepository;

    @InjectMocks DoctorScheduleServiceImpl scheduleService;

    private DoctorSchedule mondaySchedule;

    @BeforeEach
    void setUp() {
        mondaySchedule = DoctorSchedule.builder()
            .id(1L).doctorId(10L)
            .dayOfWeek(ScheduleDay.MONDAY)
            .startTime(LocalTime.of(9, 0))
            .endTime(LocalTime.of(11, 0))
            .slotDuration(30)
            .active(true)
            .build();
    }

    // ── setSchedule ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setSchedule()")
    class SetScheduleTests {

        @Test
        @DisplayName("Should create a new schedule when none exists for that day")
        void setSchedule_create_success() {
            DoctorScheduleRequest req = new DoctorScheduleRequest();
            req.setDayOfWeek(ScheduleDay.TUESDAY);
            req.setStartTime(LocalTime.of(10, 0));
            req.setEndTime(LocalTime.of(14, 0));
            req.setSlotDuration(30);

            DoctorSchedule saved = DoctorSchedule.builder()
                .id(2L).doctorId(10L).dayOfWeek(ScheduleDay.TUESDAY)
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(14, 0))
                .slotDuration(30).active(true).build();

            when(doctorScheduleRepository.findByDoctorIdAndDayOfWeek(10L, ScheduleDay.TUESDAY))
                .thenReturn(Optional.empty());
            when(doctorScheduleRepository.save(any())).thenReturn(saved);

            DoctorSchedule result = scheduleService.setSchedule(10L, req);

            assertThat(result.getDoctorId()).isEqualTo(10L);
            assertThat(result.getDayOfWeek()).isEqualTo(ScheduleDay.TUESDAY);
            assertThat(result.isActive()).isTrue();
            verify(doctorScheduleRepository).save(any());
        }

        @Test
        @DisplayName("Should update existing schedule when one already exists for that day")
        void setSchedule_update_success() {
            // Existing Monday schedule with old times
            DoctorSchedule existing = DoctorSchedule.builder()
                .id(1L).doctorId(10L).dayOfWeek(ScheduleDay.MONDAY)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .slotDuration(30).active(true).build();

            DoctorScheduleRequest req = new DoctorScheduleRequest();
            req.setDayOfWeek(ScheduleDay.MONDAY);
            req.setStartTime(LocalTime.of(9, 0));
            req.setEndTime(LocalTime.of(17, 0));
            req.setSlotDuration(45);

            when(doctorScheduleRepository.findByDoctorIdAndDayOfWeek(10L, ScheduleDay.MONDAY))
                .thenReturn(Optional.of(existing));
            when(doctorScheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DoctorSchedule result = scheduleService.setSchedule(10L, req);

            assertThat(result.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(result.getEndTime()).isEqualTo(LocalTime.of(17, 0));
            assertThat(result.getSlotDuration()).isEqualTo(45);
        }
    }

    // ── getAvailableSlots ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAvailableSlots()")
    class GetAvailableSlotsTests {

        // Monday date — find next upcoming Monday
        private final LocalDate MONDAY_DATE =
            LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(
                LocalDate.now().getDayOfWeek() == java.time.DayOfWeek.MONDAY ? 0 : 1);

        @Test
        @DisplayName("Should return all slots as available when no appointments or blocks")
        void getAvailableSlots_allAvailable() {
            when(doctorScheduleRepository.findByDoctorIdAndDayOfWeek(10L, ScheduleDay.MONDAY))
                .thenReturn(Optional.of(mondaySchedule));
            when(appointmentRepository.findBookedSlotsForDate(10L, MONDAY_DATE))
                .thenReturn(List.of());
            when(blockedSlotRepository.findByDoctorIdAndBlockedDate(10L, MONDAY_DATE))
                .thenReturn(List.of());

            List<SlotResponse> slots = scheduleService.getAvailableSlots(10L, MONDAY_DATE);

            // 9:00-11:00 with 30-min slots = 4 slots
            assertThat(slots).hasSize(4);
            assertThat(slots).allMatch(SlotResponse::isAvailable);
            assertThat(slots.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(slots.get(3).getEndTime()).isEqualTo(LocalTime.of(11, 0));
        }

        @Test
        @DisplayName("Should mark slot as unavailable when appointment exists in that slot")
        void getAvailableSlots_withBookedAppointment() {
            Appointment booked = Appointment.builder()
                .id(1L).doctorId(10L).patientId(5L)
                .appointmentDate(MONDAY_DATE)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .status(AppointmentStatus.SCHEDULED)
                .type(AppointmentType.CONSULTATION)
                .build();

            when(doctorScheduleRepository.findByDoctorIdAndDayOfWeek(10L, ScheduleDay.MONDAY))
                .thenReturn(Optional.of(mondaySchedule));
            when(appointmentRepository.findBookedSlotsForDate(10L, MONDAY_DATE))
                .thenReturn(List.of(booked));
            when(blockedSlotRepository.findByDoctorIdAndBlockedDate(10L, MONDAY_DATE))
                .thenReturn(List.of());

            List<SlotResponse> slots = scheduleService.getAvailableSlots(10L, MONDAY_DATE);

            assertThat(slots).hasSize(4);
            assertThat(slots.get(0).isAvailable()).isFalse(); // 9:00-9:30 is booked
            assertThat(slots.get(1).isAvailable()).isTrue();  // 9:30-10:00 is free
        }

        @Test
        @DisplayName("Should mark slot as unavailable when it is blocked")
        void getAvailableSlots_withBlockedSlot() {
            BlockedSlot blocked = BlockedSlot.builder()
                .id(1L).doctorId(10L)
                .blockedDate(MONDAY_DATE)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .reason("Personal appointment")
                .build();

            when(doctorScheduleRepository.findByDoctorIdAndDayOfWeek(10L, ScheduleDay.MONDAY))
                .thenReturn(Optional.of(mondaySchedule));
            when(appointmentRepository.findBookedSlotsForDate(10L, MONDAY_DATE))
                .thenReturn(List.of());
            when(blockedSlotRepository.findByDoctorIdAndBlockedDate(10L, MONDAY_DATE))
                .thenReturn(List.of(blocked));

            List<SlotResponse> slots = scheduleService.getAvailableSlots(10L, MONDAY_DATE);

            assertThat(slots).hasSize(4);
            assertThat(slots.get(0).isAvailable()).isTrue();  // 9:00-9:30 free
            assertThat(slots.get(2).isAvailable()).isFalse(); // 10:00-10:30 blocked
        }

        @Test
        @DisplayName("Should return empty list when no schedule exists for that day")
        void getAvailableSlots_noSchedule_returnsEmpty() {
            LocalDate wednesday = MONDAY_DATE.plusDays(2);
            when(doctorScheduleRepository.findByDoctorIdAndDayOfWeek(10L, ScheduleDay.WEDNESDAY))
                .thenReturn(Optional.empty());

            List<SlotResponse> slots = scheduleService.getAvailableSlots(10L, wednesday);

            assertThat(slots).isEmpty();
        }
    }

    // ── blockSlot ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("blockSlot()")
    class BlockSlotTests {

        @Test
        @DisplayName("Should save blocked slot with correct doctorId and details")
        void blockSlot_success() {
            LocalDate date = LocalDate.now().plusDays(3);
            BlockedSlotRequest req = new BlockedSlotRequest();
            req.setBlockedDate(date);
            req.setStartTime(LocalTime.of(14, 0));
            req.setEndTime(LocalTime.of(14, 30));
            req.setReason("Conference");

            scheduleService.blockSlot(10L, req);

            ArgumentCaptor<BlockedSlot> captor = ArgumentCaptor.forClass(BlockedSlot.class);
            verify(blockedSlotRepository).save(captor.capture());
            BlockedSlot saved = captor.getValue();
            assertThat(saved.getDoctorId()).isEqualTo(10L);
            assertThat(saved.getBlockedDate()).isEqualTo(date);
            assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(14, 0));
            assertThat(saved.getReason()).isEqualTo("Conference");
        }
    }

    // ── getDoctorSchedules ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDoctorSchedules()")
    class GetDoctorSchedulesTests {

        @Test
        @DisplayName("Should return active schedules for the doctor")
        void getDoctorSchedules_success() {
            when(doctorScheduleRepository.findByDoctorIdAndActiveTrue(10L))
                .thenReturn(List.of(mondaySchedule));

            List<DoctorSchedule> result = scheduleService.getDoctorSchedules(10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDayOfWeek()).isEqualTo(ScheduleDay.MONDAY);
        }

        @Test
        @DisplayName("Should return empty list when doctor has no active schedules")
        void getDoctorSchedules_empty() {
            when(doctorScheduleRepository.findByDoctorIdAndActiveTrue(10L))
                .thenReturn(List.of());

            List<DoctorSchedule> result = scheduleService.getDoctorSchedules(10L);

            assertThat(result).isEmpty();
        }
    }
}
