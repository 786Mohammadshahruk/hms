package com.hms.medical.controller;

import com.hms.medical.dto.request.OrderTestRequest;
import com.hms.medical.dto.request.UpdateTestResultRequest;
import com.hms.medical.dto.response.ApiResponse;
import com.hms.medical.dto.response.MedicalTestResponse;
import com.hms.medical.dto.response.PagedResponse;
import com.hms.medical.security.UserPrincipal;
import com.hms.medical.service.MedicalTestService;
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
@RequestMapping("/api/v1/tests")
@RequiredArgsConstructor
@Tag(name = "Medical Tests", description = "Lab test ordering and result APIs")
public class MedicalTestController {

    private final MedicalTestService medicalTestService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Order a lab test")
    public ResponseEntity<ApiResponse<MedicalTestResponse>> orderTest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrderTestRequest request) {
        MedicalTestResponse response = medicalTestService.orderTest(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Test ordered", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','PATIENT','ADMIN','LAB_TECHNICIAN')")
    @Operation(summary = "Get test by ID")
    public ResponseEntity<ApiResponse<MedicalTestResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Test retrieved", medicalTestService.getTest(id)));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN') or (hasRole('PATIENT') and #patientId == authentication.principal.userId)")
    @Operation(summary = "Get tests for patient")
    public ResponseEntity<ApiResponse<PagedResponse<MedicalTestResponse>>> getForPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Tests retrieved",
                medicalTestService.getTestsForPatient(patientId, page, size)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Get my tests (patient)")
    public ResponseEntity<ApiResponse<PagedResponse<MedicalTestResponse>>> getMyTests(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Tests retrieved",
                medicalTestService.getTestsForPatient(principal.getUserId(), page, size)));
    }

    @GetMapping("/doctor/my")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Get tests ordered by me (doctor)")
    public ResponseEntity<ApiResponse<PagedResponse<MedicalTestResponse>>> getDoctorTests(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Tests retrieved",
                medicalTestService.getTestsByDoctor(principal.getUserId(), page, size)));
    }

    @PutMapping("/{id}/result")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','ADMIN')")
    @Operation(summary = "Upload test result")
    public ResponseEntity<ApiResponse<MedicalTestResponse>> uploadResult(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTestResultRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Result uploaded",
                medicalTestService.updateResult(id, request)));
    }
}
