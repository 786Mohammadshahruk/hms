package com.hms.appointment.dto.request;

import com.hms.appointment.enums.ScheduleDay;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

// ── Reschedule ─────────────────────────────────────────────────────────────────

@Data
public class RescheduleRequest {

    @NotNull(message = "New appointment date is required")
    @Future(message = "New date must be in the future")
    private LocalDate newDate;

    @NotNull(message = "New start time is required")
    private LocalTime newStartTime;

    @Size(max = 255)
    private String reason;
}
