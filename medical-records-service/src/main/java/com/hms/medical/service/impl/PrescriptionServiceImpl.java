package com.hms.medical.service.impl;

import com.hms.medical.dto.request.CreatePrescriptionRequest;
import com.hms.medical.dto.response.MedicineResponse;
import com.hms.medical.dto.response.PagedResponse;
import com.hms.medical.dto.response.PrescriptionResponse;
import com.hms.medical.entity.Prescription;
import com.hms.medical.entity.PrescriptionMedicine;
import com.hms.medical.enums.PrescriptionStatus;
import com.hms.medical.exception.BadRequestException;
import com.hms.medical.exception.ResourceNotFoundException;
import com.hms.medical.kafka.MedicalEventPublisher;
import com.hms.medical.kafka.MedicalEvents;
import com.hms.medical.repository.PrescriptionRepository;
import com.hms.medical.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository  prescriptionRepository;
    private final MedicalEventPublisher   eventPublisher;

    @Override
    @Transactional
    public PrescriptionResponse createPrescription(Long doctorId, CreatePrescriptionRequest req) {
        Prescription prescription = Prescription.builder()
                .patientId(req.getPatientId())
                .doctorId(doctorId)
                .appointmentId(req.getAppointmentId())
                .diagnosis(req.getDiagnosis())
                .notes(req.getNotes())
                .validUntil(req.getValidUntil())
                .build();

        List<PrescriptionMedicine> medicines = req.getMedicines().stream()
                .map(m -> PrescriptionMedicine.builder()
                        .prescription(prescription)
                        .medicineName(m.getMedicineName())
                        .dosage(m.getDosage())
                        .frequency(m.getFrequency())
                        .durationDays(m.getDurationDays())
                        .instructions(m.getInstructions())
                        .quantity(m.getQuantity())
                        .build())
                .toList();

        prescription.getMedicines().addAll(medicines);
        Prescription saved = prescriptionRepository.save(prescription);

        eventPublisher.publishPrescriptionCreated(new MedicalEvents.PrescriptionCreatedEvent(
                saved.getId(),
                saved.getPatientId(),
                saved.getDoctorId(),
                saved.getDiagnosis(),
                medicines.stream().map(PrescriptionMedicine::getMedicineName).toList(),
                saved.getValidUntil()
        ));

        log.info("Prescription {} created by doctorId={}", saved.getId(), doctorId);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionResponse getPrescription(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PrescriptionResponse> getPrescriptionsForPatient(Long patientId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.from(prescriptionRepository.findByPatientId(patientId, pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PrescriptionResponse> getPrescriptionsByDoctor(Long doctorId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.from(prescriptionRepository.findByDoctorId(doctorId, pageable).map(this::toResponse));
    }

    @Override
    @Transactional
    public PrescriptionResponse cancelPrescription(Long id, Long doctorId) {
        Prescription prescription = findById(id);
        if (!prescription.getDoctorId().equals(doctorId)) {
            throw new BadRequestException("You are not authorized to cancel this prescription");
        }
        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new BadRequestException("Prescription is already cancelled");
        }
        prescription.setStatus(PrescriptionStatus.CANCELLED);
        return toResponse(prescriptionRepository.save(prescription));
    }

    private Prescription findById(Long id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + id));
    }

    private PrescriptionResponse toResponse(Prescription p) {
        List<MedicineResponse> medicineResponses = p.getMedicines().stream()
                .map(m -> MedicineResponse.builder()
                        .id(m.getId())
                        .medicineName(m.getMedicineName())
                        .dosage(m.getDosage())
                        .frequency(m.getFrequency())
                        .durationDays(m.getDurationDays())
                        .instructions(m.getInstructions())
                        .quantity(m.getQuantity())
                        .build())
                .toList();

        return PrescriptionResponse.builder()
                .id(p.getId())
                .uuid(p.getUuid())
                .patientId(p.getPatientId())
                .doctorId(p.getDoctorId())
                .appointmentId(p.getAppointmentId())
                .diagnosis(p.getDiagnosis())
                .notes(p.getNotes())
                .validUntil(p.getValidUntil())
                .status(p.getStatus())
                .medicines(medicineResponses)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
