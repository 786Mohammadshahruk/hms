package com.hms.billing.controller;

import com.hms.billing.dto.request.CreateBillRequest;
import com.hms.billing.dto.response.ApiResponse;
import com.hms.billing.dto.response.BillResponse;
import com.hms.billing.dto.response.PagedResponse;
import com.hms.billing.security.UserPrincipal;
import com.hms.billing.service.BillService;
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
@RequestMapping("/api/v1/bills")
@RequiredArgsConstructor
@Tag(name = "Bills", description = "Bill management APIs")
public class BillController {

    private final BillService billService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    @Operation(summary = "Create a new bill")
    public ResponseEntity<ApiResponse<BillResponse>> create(@Valid @RequestBody CreateBillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bill created", billService.createBill(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','DOCTOR') or hasRole('PATIENT')")
    @Operation(summary = "Get bill by ID")
    public ResponseEntity<ApiResponse<BillResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Bill retrieved", billService.getBill(id)));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST') or (hasRole('PATIENT') and #patientId == authentication.principal.userId)")
    @Operation(summary = "Get bills for a patient")
    public ResponseEntity<ApiResponse<PagedResponse<BillResponse>>> getForPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Bills retrieved",
                billService.getBillsForPatient(patientId, page, size)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Get my bills (patient)")
    public ResponseEntity<ApiResponse<PagedResponse<BillResponse>>> getMyBills(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Bills retrieved",
                billService.getBillsForPatient(principal.getUserId(), page, size)));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    @Operation(summary = "Cancel a bill")
    public ResponseEntity<ApiResponse<BillResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Bill cancelled", billService.cancelBill(id)));
    }
}
