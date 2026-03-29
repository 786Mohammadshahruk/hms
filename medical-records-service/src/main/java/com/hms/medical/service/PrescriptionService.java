package com.hms.medical.service;

import com.hms.medical.dto.request.CreatePrescriptionRequest;
import com.hms.medical.dto.response.PagedResponse;
import com.hms.medical.dto.response.PrescriptionResponse;

public interface PrescriptionService {

    PrescriptionResponse createPrescription(Long doctorId, CreatePrescriptionRequest request);

    PrescriptionResponse getPrescription(Long id);

    PagedResponse<PrescriptionResponse> getPrescriptionsForPatient(Long patientId, int page, int size);

    PagedResponse<PrescriptionResponse> getPrescriptionsByDoctor(Long doctorId, int page, int size);

    PrescriptionResponse cancelPrescription(Long id, Long doctorId);
}
