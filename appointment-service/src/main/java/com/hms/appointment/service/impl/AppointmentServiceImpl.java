package com.hms.appointment.service.impl;

import com.hms.appointment.client.UserServiceClient;
import com.hms.appointment.dto.request.BookAppointmentRequest;
import com.hms.appointment.dto.request.CancelAppointmentRequest;
import com.hms.appointment.dto.request.RescheduleRequest;
import com.hms.appointment.dto.response.AppointmentResponse;
import com.hms.appointment.dto.response.PagedResponse;
import com.hms.appointment.entity.Appointment;
import com.hms.appointment.entity.DoctorSchedule;
import com.hms.appointment.enums.AppointmentStatus;
import com.hms.appointment.enums.ScheduleDay;
import com.hms.appointment.exception.BadRequestException;
import com.hms.appointment.exception.ConflictException;
import com.hms.appointment.exception.ResourceNotFoundException;
import com.hms.appointment.kafka.AppointmentEventPublisher;
import com.hms.appointment.kafka.AppointmentEvents;
import com.hms.appointment.repository.AppointmentRepository;
import com.hms.appointment.repository.DoctorScheduleRepository;
import com.hms.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository      appointmentRepository;
    private final DoctorScheduleRepository   doctorScheduleRepository;
    private final AppointmentEventPublisher  eventPublisher;
    private final UserServiceClient          userServiceClient;

    // ── Book ───────────────────────────────────────────────────────────────────

    @Override
    public AppointmentResponse bookAppointment(Long patientId, BookAppointmentRequest request) {
        log.info("Booking appointment for patientId={} with doctorId={}", patientId, request.getDoctorId());

        LocalDate date      = request.getAppointmentDate();
        LocalTime startTime = request.getStartTime();
        Long      doctorId  = request.getDoctorId();

        // Fetch doctor schedule for this day
        ScheduleDay day = ScheduleDay.valueOf(date.getDayOfWeek().name());
        DoctorSchedule schedule = doctorScheduleRepository
            .findByDoctorIdAndDayOfWeek(doctorId, day)
            .orElseThrow(() -> new BadRequestException(
                "Doctor has no schedule for " + day + ". Please choose another date."));

        if (!schedule.isActive()) {
            throw new BadRequestException("Doctor's schedule for " + day + " is not active.");
        }

        // Validate start time is within schedule
        if (startTime.isBefore(schedule.getStartTime()) ||
            startTime.isAfter(schedule.getEndTime().minusMinutes(schedule.getSlotDuration()))) {
            throw new BadRequestException(
                "Start time " + startTime + " is outside doctor's working hours " +
                schedule.getStartTime() + " - " + schedule.getEndTime());
        }

        LocalTime endTime = startTime.plusMinutes(schedule.getSlotDuration());

        // Check for conflicting appointments
        List<Appointment> conflicts = appointmentRepository.findConflicting(doctorId, date, startTime, endTime);
        if (!conflicts.isEmpty()) {
            throw new ConflictException("The requested time slot is already booked. Please choose another slot.");
        }

        // Build and save appointment
        Appointment appointment = Appointment.builder()
            .patientId(patientId)
            .doctorId(doctorId)
            .appointmentDate(date)
            .startTime(startTime)
            .endTime(endTime)
            .status(AppointmentStatus.SCHEDULED)
            .type(request.getType())
            .reason(request.getReason())
            .build();

        appointment = appointmentRepository.save(appointment);
        log.info("Appointment booked: id={}, uuid={}", appointment.getId(), appointment.getUuid());

        // Fetch user info for notification (best-effort)
        String patientEmail = userServiceClient.getUserById(patientId)
            .map(UserServiceClient.UserInfo::getEmail).orElse(null);
        String doctorName = userServiceClient.getUserById(doctorId)
            .map(UserServiceClient.UserInfo::getFullName).orElse("Doctor #" + doctorId);

        // Publish event
        eventPublisher.publishAppointmentBooked(AppointmentEvents.AppointmentBookedEvent.builder()
            .appointmentId(appointment.getId())
            .appointmentUuid(appointment.getUuid())
            .patientId(patientId)
            .patientEmail(patientEmail)
            .doctorId(doctorId)
            .doctorName(doctorName)
            .appointmentDate(date)
            .startTime(startTime)
            .type(request.getType())
            .occurredAt(Instant.now())
            .build());

        return toResponse(appointment, patientEmail, null, doctorName);
    }

    // ── Reschedule ─────────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "appointments", key = "#appointmentId")
    public AppointmentResponse rescheduleAppointment(Long appointmentId, Long patientId,
                                                      RescheduleRequest request) {
        Appointment appt = findById(appointmentId);

        if (!appt.getPatientId().equals(patientId)) {
            throw new BadRequestException("You can only reschedule your own appointments.");
        }
        if (appt.getStatus() == AppointmentStatus.CANCELLED ||
            appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestException("Cannot reschedule a " + appt.getStatus().name().toLowerCase() + " appointment.");
        }

        LocalDate newDate      = request.getNewDate();
        LocalTime newStartTime = request.getNewStartTime();
        Long      doctorId     = appt.getDoctorId();

        ScheduleDay day = ScheduleDay.valueOf(newDate.getDayOfWeek().name());
        DoctorSchedule schedule = doctorScheduleRepository
            .findByDoctorIdAndDayOfWeek(doctorId, day)
            .orElseThrow(() -> new BadRequestException("Doctor has no schedule for " + day));

        LocalTime newEndTime = newStartTime.plusMinutes(schedule.getSlotDuration());

        List<Appointment> conflicts = appointmentRepository.findConflicting(doctorId, newDate, newStartTime, newEndTime)
            .stream().filter(c -> !c.getId().equals(appointmentId)).toList();

        if (!conflicts.isEmpty()) {
            throw new ConflictException("The new time slot is already booked.");
        }

        appt.setAppointmentDate(newDate);
        appt.setStartTime(newStartTime);
        appt.setEndTime(newEndTime);
        appt.setStatus(AppointmentStatus.SCHEDULED);
        if (request.getReason() != null) appt.setNotes(request.getReason());

        appt = appointmentRepository.save(appt);
        log.info("Appointment rescheduled: id={}", appointmentId);
        return toResponse(appt);
    }

    // ── Cancel ─────────────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "appointments", key = "#appointmentId")
    public AppointmentResponse cancelAppointment(Long appointmentId, Long patientId,
                                                  CancelAppointmentRequest request) {
        Appointment appt = findById(appointmentId);

        if (!appt.getPatientId().equals(patientId)) {
            throw new BadRequestException("You can only cancel your own appointments.");
        }
        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestException("Appointment is already cancelled.");
        }
        if (appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a completed appointment.");
        }

        appt.setStatus(AppointmentStatus.CANCELLED);
        appt.setCancellationReason(request.getReason());
        appt = appointmentRepository.save(appt);

        String patientEmail = userServiceClient.getUserById(patientId)
            .map(UserServiceClient.UserInfo::getEmail).orElse(null);

        eventPublisher.publishAppointmentCancelled(AppointmentEvents.AppointmentCancelledEvent.builder()
            .appointmentId(appt.getId())
            .appointmentUuid(appt.getUuid())
            .patientId(patientId)
            .patientEmail(patientEmail)
            .doctorId(appt.getDoctorId())
            .cancellationReason(request.getReason())
            .occurredAt(Instant.now())
            .build());

        log.info("Appointment cancelled: id={}", appointmentId);
        return toResponse(appt);
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "appointments", key = "#id")
    public AppointmentResponse getAppointmentById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentByUuid(UUID uuid) {
        Appointment appt = appointmentRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", "uuid", uuid));
        return toResponse(appt);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AppointmentResponse> getPatientAppointments(Long patientId,
                                                                       AppointmentStatus status,
                                                                       Pageable pageable) {
        Page<Appointment> page = (status != null)
            ? appointmentRepository.findByPatientIdAndStatus(patientId, status, pageable)
            : appointmentRepository.findByPatientId(patientId, pageable);
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AppointmentResponse> getDoctorAppointments(Long doctorId, LocalDate date,
                                                                     AppointmentStatus status,
                                                                     Pageable pageable) {
        Page<Appointment> page = (date != null)
            ? appointmentRepository.findByDoctorIdAndAppointmentDate(doctorId, date, pageable)
            : appointmentRepository.findByDoctorId(doctorId, pageable);
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Override
    @CacheEvict(value = "appointments", key = "#appointmentId")
    public AppointmentResponse updateStatus(Long appointmentId, AppointmentStatus newStatus) {
        Appointment appt = findById(appointmentId);
        appt.setStatus(newStatus);
        return toResponse(appointmentRepository.save(appt));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private Appointment findById(Long id) {
        return appointmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
    }

    private AppointmentResponse toResponse(Appointment a) {
        return toResponse(a, null, null, null);
    }

    private AppointmentResponse toResponse(Appointment a, String patientEmail,
                                            String patientName, String doctorName) {
        return AppointmentResponse.builder()
            .id(a.getId())
            .uuid(a.getUuid())
            .patientId(a.getPatientId())
            .patientName(patientName)
            .doctorId(a.getDoctorId())
            .doctorName(doctorName)
            .appointmentDate(a.getAppointmentDate())
            .startTime(a.getStartTime())
            .endTime(a.getEndTime())
            .status(a.getStatus())
            .type(a.getType())
            .reason(a.getReason())
            .notes(a.getNotes())
            .cancellationReason(a.getCancellationReason())
            .createdAt(a.getCreatedAt())
            .updatedAt(a.getUpdatedAt())
            .build();
    }
}
