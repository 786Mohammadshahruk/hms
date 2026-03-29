package com.hms.medical.service;

import com.hms.medical.dto.request.OrderTestRequest;
import com.hms.medical.dto.request.UpdateTestResultRequest;
import com.hms.medical.dto.response.MedicalTestResponse;
import com.hms.medical.dto.response.PagedResponse;

public interface MedicalTestService {

    MedicalTestResponse orderTest(Long doctorId, OrderTestRequest request);

    MedicalTestResponse getTest(Long id);

    PagedResponse<MedicalTestResponse> getTestsForPatient(Long patientId, int page, int size);

    PagedResponse<MedicalTestResponse> getTestsByDoctor(Long doctorId, int page, int size);

    MedicalTestResponse updateResult(Long id, UpdateTestResultRequest request);
}
