package com.hms.appointment.controller;

import com.hms.appointment.dto.request.BlockedSlotRequest;
import com.hms.appointment.dto.request.DoctorScheduleRequest;
import com.hms.appointment.dto.response.ApiResponse;
import com.hms.appointment.dto.response.SlotResponse;
import com.hms.appointment.entity.DoctorSchedule;
import com.hms.appointment.service.DoctorScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Doctor Schedules", description = "Doctor availability and slot management")
@SecurityRequirement(name = "bearerAuth")
public class DoctorScheduleController {

    private final DoctorScheduleService scheduleService;

    @PostMapping("/doctor/{doctorId}")
    @Operation(summary = "Set or update doctor's weekly schedule")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<ApiResponse<DoctorSchedule>> setSchedule(
            @PathVariable Long doctorId,
            @Valid @RequestBody DoctorScheduleRequest request) {

        DoctorSchedule schedule = scheduleService.setSchedule(doctorId, request);
        return ResponseEntity.ok(ApiResponse.success("Schedule updated", schedule));
    }

    @GetMapping("/doctor/{doctorId}")
    @Operation(summary = "Get all active schedules for a doctor")
    public ResponseEntity<ApiResponse<List<DoctorSchedule>>> getDoctorSchedules(
            @PathVariable Long doctorId) {

        List<DoctorSchedule> schedules = scheduleService.getDoctorSchedules(doctorId);
        return ResponseEntity.ok(ApiResponse.success("Schedules fetched", schedules));
    }

    @GetMapping("/doctor/{doctorId}/slots")
    @Operation(summary = "Get available time slots for a doctor on a specific date")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAvailableSlots(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<SlotResponse> slots = scheduleService.getAvailableSlots(doctorId, date);
        return ResponseEntity.ok(ApiResponse.success("Available slots fetched", slots));
    }

    @PostMapping("/doctor/{doctorId}/block")
    @Operation(summary = "Block a specific time slot (e.g., doctor on leave)")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> blockSlot(
            @PathVariable Long doctorId,
            @Valid @RequestBody BlockedSlotRequest request) {

        scheduleService.blockSlot(doctorId, request);
        return ResponseEntity.ok(ApiResponse.success("Slot blocked successfully"));
    }
}
