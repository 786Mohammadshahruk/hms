package com.hms.billing.service;

import com.hms.billing.dto.request.CreateBillRequest;
import com.hms.billing.dto.response.BillResponse;
import com.hms.billing.dto.response.PagedResponse;

public interface BillService {

    BillResponse createBill(CreateBillRequest request);

    BillResponse getBill(Long id);

    PagedResponse<BillResponse> getBillsForPatient(Long patientId, int page, int size);

    BillResponse cancelBill(Long id);
}
