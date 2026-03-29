package com.hms.appointment.dto.request;

import com.hms.appointment.enums.AppointmentType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request body for booking a new appointment.
 */
@Data
public class BookAppointmentRequest {

    @NotNull(message = "Doctor ID is required")
    private Long doctorId;

    @NotNull(message = "Appointment date is required")
    @Future(message = "Appointment date must be in the future")
    private LocalDate appointmentDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "Appointment type is required")
    private AppointmentType type;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
