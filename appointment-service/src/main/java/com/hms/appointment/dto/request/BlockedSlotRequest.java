package com.hms.appointment.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class BlockedSlotRequest {

    @NotNull(message = "Date is required")
    @FutureOrPresent(message = "Blocked date must be today or in the future")
    private LocalDate blockedDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @Size(max = 255)
    private String reason;
}
