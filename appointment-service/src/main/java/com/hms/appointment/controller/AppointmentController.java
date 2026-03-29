package com.hms.appointment.controller;

import com.hms.appointment.dto.request.BookAppointmentRequest;
import com.hms.appointment.dto.request.CancelAppointmentRequest;
import com.hms.appointment.dto.request.RescheduleRequest;
import com.hms.appointment.dto.response.ApiResponse;
import com.hms.appointment.dto.response.AppointmentResponse;
import com.hms.appointment.dto.response.PagedResponse;
import com.hms.appointment.enums.AppointmentStatus;
import com.hms.appointment.security.UserPrincipal;
import com.hms.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments", description = "Appointment booking, rescheduling, and management")
@SecurityRequirement(name = "bearerAuth")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @Operation(summary = "Book a new appointment")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(
            @Valid @RequestBody BookAppointmentRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        AppointmentResponse response = appointmentService.bookAppointment(currentUser.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Appointment booked successfully", response));
    }

    @PutMapping("/{id}/reschedule")
    @Operation(summary = "Reschedule an existing appointment")
    @PreAuthorize("hasAnyRole('PATIENT','ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        AppointmentResponse response = appointmentService.rescheduleAppointment(id, currentUser.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Appointment rescheduled successfully", response));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an appointment")
    @PreAuthorize("hasAnyRole('PATIENT','ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(
            @PathVariable Long id,
            @Valid @RequestBody CancelAppointmentRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        AppointmentResponse response = appointmentService.cancelAppointment(id, currentUser.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Appointment cancelled successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get appointment by internal ID")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Appointment fetched", appointmentService.getAppointmentById(id)));
    }

    @GetMapping("/uuid/{uuid}")
    @Operation(summary = "Get appointment by UUID")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(ApiResponse.success("Appointment fetched", appointmentService.getAppointmentByUuid(uuid)));
    }

    @GetMapping("/my")
    @Operation(summary = "Get patient's own appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<PagedResponse<AppointmentResponse>>> getMyAppointments(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appointmentDate"));
        PagedResponse<AppointmentResponse> response =
            appointmentService.getPatientAppointments(currentUser.getUserId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Appointments fetched", response));
    }

    @GetMapping("/doctor/{doctorId}")
    @Operation(summary = "Get doctor's appointments")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<PagedResponse<AppointmentResponse>>> getDoctorAppointments(
            @PathVariable Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("appointmentDate", "startTime"));
        PagedResponse<AppointmentResponse> response =
            appointmentService.getDoctorAppointments(doctorId, date, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Appointments fetched", response));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update appointment status (DOCTOR or ADMIN only)")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam AppointmentStatus status) {

        AppointmentResponse response = appointmentService.updateStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Status updated to " + status, response));
    }
}
