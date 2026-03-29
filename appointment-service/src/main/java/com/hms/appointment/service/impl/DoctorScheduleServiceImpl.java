package com.hms.appointment.service.impl;

import com.hms.appointment.dto.request.BlockedSlotRequest;
import com.hms.appointment.dto.request.DoctorScheduleRequest;
import com.hms.appointment.dto.response.SlotResponse;
import com.hms.appointment.entity.Appointment;
import com.hms.appointment.entity.BlockedSlot;
import com.hms.appointment.entity.DoctorSchedule;
import com.hms.appointment.enums.ScheduleDay;
import com.hms.appointment.repository.AppointmentRepository;
import com.hms.appointment.repository.BlockedSlotRepository;
import com.hms.appointment.repository.DoctorScheduleRepository;
import com.hms.appointment.service.DoctorScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DoctorScheduleServiceImpl implements DoctorScheduleService {

    private final DoctorScheduleRepository doctorScheduleRepository;
    private final AppointmentRepository    appointmentRepository;
    private final BlockedSlotRepository    blockedSlotRepository;

    @Override
    public DoctorSchedule setSchedule(Long doctorId, DoctorScheduleRequest request) {
        DoctorSchedule schedule = doctorScheduleRepository
            .findByDoctorIdAndDayOfWeek(doctorId, request.getDayOfWeek())
            .orElse(DoctorSchedule.builder().doctorId(doctorId).build());

        schedule.setDayOfWeek(request.getDayOfWeek());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setSlotDuration(request.getSlotDuration());
        schedule.setActive(true);

        DoctorSchedule saved = doctorScheduleRepository.save(schedule);
        log.info("Schedule set for doctorId={} day={}", doctorId, request.getDayOfWeek());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorSchedule> getDoctorSchedules(Long doctorId) {
        return doctorScheduleRepository.findByDoctorIdAndActiveTrue(doctorId);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "doctor-slots", key = "#doctorId + ':' + #date")
    public List<SlotResponse> getAvailableSlots(Long doctorId, LocalDate date) {
        ScheduleDay day = ScheduleDay.valueOf(date.getDayOfWeek().name());

        return doctorScheduleRepository
            .findByDoctorIdAndDayOfWeek(doctorId, day)
            .filter(DoctorSchedule::isActive)
            .map(schedule -> computeAvailableSlots(doctorId, date, schedule))
            .orElse(List.of());
    }

    @Override
    @CacheEvict(value = "doctor-slots", key = "#doctorId + ':' + #request.blockedDate")
    public void blockSlot(Long doctorId, BlockedSlotRequest request) {
        BlockedSlot slot = BlockedSlot.builder()
            .doctorId(doctorId)
            .blockedDate(request.getBlockedDate())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .reason(request.getReason())
            .build();
        blockedSlotRepository.save(slot);
        log.info("Slot blocked for doctorId={} date={} {}–{}", doctorId,
            request.getBlockedDate(), request.getStartTime(), request.getEndTime());
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private List<SlotResponse> computeAvailableSlots(Long doctorId, LocalDate date, DoctorSchedule schedule) {
        List<Appointment> bookedSlots     = appointmentRepository.findBookedSlotsForDate(doctorId, date);
        List<BlockedSlot> blockedSlots    = blockedSlotRepository.findByDoctorIdAndBlockedDate(doctorId, date);
        List<SlotResponse> availableSlots = new ArrayList<>();

        LocalTime current = schedule.getStartTime();
        LocalTime end     = schedule.getEndTime();
        int       duration = schedule.getSlotDuration();

        while (!current.isAfter(end.minusMinutes(duration))) {
            LocalTime slotEnd   = current.plusMinutes(duration);
            boolean   available = isSlotAvailable(current, slotEnd, bookedSlots, blockedSlots);
            availableSlots.add(SlotResponse.builder()
                .startTime(current)
                .endTime(slotEnd)
                .available(available)
                .build());
            current = slotEnd;
        }

        return availableSlots;
    }

    private boolean isSlotAvailable(LocalTime start, LocalTime end,
                                     List<Appointment> booked, List<BlockedSlot> blocked) {
        for (Appointment a : booked) {
            if (start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime())) return false;
        }
        for (BlockedSlot b : blocked) {
            if (start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime())) return false;
        }
        return true;
    }
}
