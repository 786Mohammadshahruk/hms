package com.hms.appointment.dto.request;

import com.hms.appointment.enums.ScheduleDay;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CancelAppointmentRequest {

    @NotBlank(message = "Cancellation reason is required")
    @Size(max = 500)
    private String reason;
}
