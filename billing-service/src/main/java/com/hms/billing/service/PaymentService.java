package com.hms.billing.service;

import com.hms.billing.dto.request.ProcessPaymentRequest;
import com.hms.billing.dto.response.PagedResponse;
import com.hms.billing.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse processPayment(Long patientId, ProcessPaymentRequest request);

    PaymentResponse getPayment(Long id);

    PagedResponse<PaymentResponse> getPaymentsForBill(Long billId, int page, int size);

    PagedResponse<PaymentResponse> getPaymentsForPatient(Long patientId, int page, int size);
}
