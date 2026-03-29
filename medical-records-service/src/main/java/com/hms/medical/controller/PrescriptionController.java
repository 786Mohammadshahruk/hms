package com.hms.medical.controller;

import com.hms.medical.dto.request.CreatePrescriptionRequest;
import com.hms.medical.dto.response.ApiResponse;
import com.hms.medical.dto.response.PagedResponse;
import com.hms.medical.dto.response.PrescriptionResponse;
import com.hms.medical.security.UserPrincipal;
import com.hms.medical.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/prescriptions")
@RequiredArgsConstructor
@Tag(name = "Prescriptions", description = "Prescription management APIs")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Create prescription")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        PrescriptionResponse response = prescriptionService.createPrescription(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Prescription created", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','PATIENT','ADMIN')")
    @Operation(summary = "Get prescription by ID")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Prescription retrieved", prescriptionService.getPrescription(id)));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN') or (hasRole('PATIENT') and #patientId == authentication.principal.userId)")
    @Operation(summary = "Get prescriptions for patient")
    public ResponseEntity<ApiResponse<PagedResponse<PrescriptionResponse>>> getForPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Prescriptions retrieved",
                prescriptionService.getPrescriptionsForPatient(patientId, page, size)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Get my prescriptions (patient)")
    public ResponseEntity<ApiResponse<PagedResponse<PrescriptionResponse>>> getMyPrescriptions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Prescriptions retrieved",
                prescriptionService.getPrescriptionsForPatient(principal.getUserId(), page, size)));
    }

    @GetMapping("/doctor/my")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Get prescriptions written by me (doctor)")
    public ResponseEntity<ApiResponse<PagedResponse<PrescriptionResponse>>> getDoctorPrescriptions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Prescriptions retrieved",
                prescriptionService.getPrescriptionsByDoctor(principal.getUserId(), page, size)));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Cancel prescription")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Prescription cancelled",
                prescriptionService.cancelPrescription(id, principal.getUserId())));
    }
}
