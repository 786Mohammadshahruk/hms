package com.hms.appointment.service;

import com.hms.appointment.dto.request.BookAppointmentRequest;
import com.hms.appointment.dto.request.CancelAppointmentRequest;
import com.hms.appointment.dto.request.RescheduleRequest;
import com.hms.appointment.dto.response.AppointmentResponse;
import com.hms.appointment.dto.response.PagedResponse;
import com.hms.appointment.enums.AppointmentStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface AppointmentService {

    AppointmentResponse bookAppointment(Long patientId, BookAppointmentRequest request);

    AppointmentResponse rescheduleAppointment(Long appointmentId, Long patientId, RescheduleRequest request);

    AppointmentResponse cancelAppointment(Long appointmentId, Long patientId, CancelAppointmentRequest request);

    AppointmentResponse getAppointmentById(Long id);

    AppointmentResponse getAppointmentByUuid(UUID uuid);

    PagedResponse<AppointmentResponse> getPatientAppointments(Long patientId, AppointmentStatus status, Pageable pageable);

    PagedResponse<AppointmentResponse> getDoctorAppointments(Long doctorId, LocalDate date, AppointmentStatus status, Pageable pageable);

    AppointmentResponse updateStatus(Long appointmentId, AppointmentStatus newStatus);
}
