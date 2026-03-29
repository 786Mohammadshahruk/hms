package com.hms.appointment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlotResponse {
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final boolean   available;
}
