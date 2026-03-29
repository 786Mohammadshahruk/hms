package com.hms.billing.controller;

import com.hms.billing.dto.request.ProcessPaymentRequest;
import com.hms.billing.dto.response.ApiResponse;
import com.hms.billing.dto.response.PagedResponse;
import com.hms.billing.dto.response.PaymentResponse;
import com.hms.billing.security.UserPrincipal;
import com.hms.billing.service.PaymentService;
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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('PATIENT','ADMIN','RECEPTIONIST')")
    @Operation(summary = "Process a payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProcessPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment processed",
                        paymentService.processPayment(principal.getUserId(), request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','PATIENT')")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved", paymentService.getPayment(id)));
    }

    @GetMapping("/bill/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','PATIENT')")
    @Operation(summary = "Get payments for a bill")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> getForBill(
            @PathVariable Long billId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved",
                paymentService.getPaymentsForBill(billId, page, size)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Get my payment history")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> getMyPayments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved",
                paymentService.getPaymentsForPatient(principal.getUserId(), page, size)));
    }
}
