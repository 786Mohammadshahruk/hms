package com.hms.appointment.service;

import com.hms.appointment.client.UserServiceClient;
import com.hms.appointment.dto.request.BookAppointmentRequest;
import com.hms.appointment.dto.request.CancelAppointmentRequest;
import com.hms.appointment.dto.response.AppointmentResponse;
import com.hms.appointment.entity.Appointment;
import com.hms.appointment.entity.DoctorSchedule;
import com.hms.appointment.enums.AppointmentStatus;
import com.hms.appointment.enums.AppointmentType;
import com.hms.appointment.enums.ScheduleDay;
import com.hms.appointment.exception.BadRequestException;
import com.hms.appointment.exception.ConflictException;
import com.hms.appointment.exception.ResourceNotFoundException;
import com.hms.appointment.kafka.AppointmentEventPublisher;
import com.hms.appointment.repository.AppointmentRepository;
import com.hms.appointment.repository.BlockedSlotRepository;
import com.hms.appointment.repository.DoctorScheduleRepository;
import com.hms.appointment.service.impl.AppointmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService Tests")
class AppointmentServiceTest {

    @Mock AppointmentRepository     appointmentRepository;
    @Mock DoctorScheduleRepository  doctorScheduleRepository;
    @Mock BlockedSlotRepository     blockedSlotRepository;
    @Mock AppointmentEventPublisher eventPublisher;
    @Mock UserServiceClient         userServiceClient;

    @InjectMocks AppointmentServiceImpl appointmentService;

    private DoctorSchedule testSchedule;
    private Appointment    testAppointment;

    @BeforeEach
    void setUp() {
        testSchedule = DoctorSchedule.builder()
            .id(1L).doctorId(10L)
            .dayOfWeek(ScheduleDay.MONDAY)
            .startTime(LocalTime.of(9, 0))
            .endTime(LocalTime.of(17, 0))
            .slotDuration(30)
            .active(true)
            .build();

        testAppointment = Appointment.builder()
            .id(1L).uuid(UUID.randomUUID())
            .patientId(1L).doctorId(10L)
            .appointmentDate(LocalDate.now().plusDays(7))
            .startTime(LocalTime.of(10, 0))
            .endTime(LocalTime.of(10, 30))
            .status(AppointmentStatus.SCHEDULED)
            .type(AppointmentType.CONSULTATION)
            .build();
    }

    @Test
    @DisplayName("bookAppointment — should book successfully when no conflicts")
    void bookAppointment_success() {
        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setDoctorId(10L);
        request.setAppointmentDate(LocalDate.now().plusDays(7)); // Monday
        request.setStartTime(LocalTime.of(10, 0));
        request.setType(AppointmentType.CONSULTATION);

        // Make the date fall on Monday
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(1);
        request.setAppointmentDate(monday);

        when(doctorScheduleRepository.findByDoctorIdAndDayOfWeek(10L, ScheduleDay.MONDAY))
            .thenReturn(Optional.of(testSchedule));
        when(appointmentRepository.findConflicting(any(), any(), any(), any()))
            .thenReturn(List.of());
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(testAppointment);
        when(userServiceClient.getUserById(anyLong())).thenReturn(Optional.empty());

        AppointmentResponse response = appointmentService.bookAppointment(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(appointmentRepository).save(any(Appointment.class));
        verify(eventPublisher).publishAppointmentBooked(any());
    }

    @Test
    @DisplayName("bookAppointment — should throw ConflictException when slot is taken")
    void bookAppointment_conflictThrowsException() {
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(1);
        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setDoctorId(10L);
        request.setAppointmentDate(monday);
        request.setStartTime(LocalTime.of(10, 0));
        request.setType(AppointmentType.CONSULTATION);

        when(doctorScheduleRepository.findByDoctorIdAndDayOfWeek(10L, ScheduleDay.MONDAY))
            .thenReturn(Optional.of(testSchedule));
        when(appointmentRepository.findConflicting(any(), any(), any(), any()))
            .thenReturn(List.of(testAppointment));

        assertThatThrownBy(() -> appointmentService.bookAppointment(1L, request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("time slot is already booked");

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("bookAppointment — should throw BadRequestException when no schedule")
    void bookAppointment_noScheduleThrowsException() {
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(1);
        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setDoctorId(10L);
        request.setAppointmentDate(monday);
        request.setStartTime(LocalTime.of(10, 0));
        request.setType(AppointmentType.CONSULTATION);

        when(doctorScheduleRepository.findByDoctorIdAndDayOfWeek(any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.bookAppointment(1L, request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("no schedule");
    }

    @Test
    @DisplayName("cancelAppointment — should cancel successfully")
    void cancelAppointment_success() {
        CancelAppointmentRequest request = new CancelAppointmentRequest();
        request.setReason("Personal emergency");

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any())).thenReturn(testAppointment);
        when(userServiceClient.getUserById(anyLong())).thenReturn(Optional.empty());

        AppointmentResponse response = appointmentService.cancelAppointment(1L, 1L, request);

        assertThat(response).isNotNull();
        verify(appointmentRepository).save(any());
        verify(eventPublisher).publishAppointmentCancelled(any());
    }

    @Test
    @DisplayName("cancelAppointment — should throw BadRequestException when not own appointment")
    void cancelAppointment_wrongPatient_throwsException() {
        CancelAppointmentRequest request = new CancelAppointmentRequest();
        request.setReason("Test");

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));

        // patientId=1 on appointment, but requesting user is patientId=99
        assertThatThrownBy(() -> appointmentService.cancelAppointment(1L, 99L, request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("own appointments");
    }

    @Test
    @DisplayName("getAppointmentById — should throw ResourceNotFoundException when not found")
    void getAppointmentById_notFound() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.getAppointmentById(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Appointment");
    }
}
