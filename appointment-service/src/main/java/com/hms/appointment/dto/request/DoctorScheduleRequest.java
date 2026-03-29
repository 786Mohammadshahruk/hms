package com.hms.appointment.dto.request;

import com.hms.appointment.enums.ScheduleDay;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalTime;

@Data
public class DoctorScheduleRequest {

    @NotNull(message = "Day of week is required")
    private ScheduleDay dayOfWeek;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @Min(value = 10, message = "Slot duration must be at least 10 minutes")
    @Max(value = 120, message = "Slot duration must not exceed 120 minutes")
    private int slotDuration = 30;
}
