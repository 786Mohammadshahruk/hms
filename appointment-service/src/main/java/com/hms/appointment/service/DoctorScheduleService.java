package com.hms.appointment.service;

import com.hms.appointment.dto.request.BlockedSlotRequest;
import com.hms.appointment.dto.request.DoctorScheduleRequest;
import com.hms.appointment.dto.response.SlotResponse;
import com.hms.appointment.entity.DoctorSchedule;

import java.time.LocalDate;
import java.util.List;

public interface DoctorScheduleService {

    DoctorSchedule setSchedule(Long doctorId, DoctorScheduleRequest request);

    List<DoctorSchedule> getDoctorSchedules(Long doctorId);

    List<SlotResponse> getAvailableSlots(Long doctorId, LocalDate date);

    void blockSlot(Long doctorId, BlockedSlotRequest request);
}
