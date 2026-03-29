package com.hms.medical.service.impl;

import com.hms.medical.dto.request.OrderTestRequest;
import com.hms.medical.dto.request.UpdateTestResultRequest;
import com.hms.medical.dto.response.MedicalTestResponse;
import com.hms.medical.dto.response.PagedResponse;
import com.hms.medical.entity.MedicalTest;
import com.hms.medical.enums.TestStatus;
import com.hms.medical.exception.BadRequestException;
import com.hms.medical.exception.ResourceNotFoundException;
import com.hms.medical.kafka.MedicalEventPublisher;
import com.hms.medical.kafka.MedicalEvents;
import com.hms.medical.repository.MedicalTestRepository;
import com.hms.medical.service.MedicalTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalTestServiceImpl implements MedicalTestService {

    private final MedicalTestRepository medicalTestRepository;
    private final MedicalEventPublisher eventPublisher;

    @Override
    @Transactional
    public MedicalTestResponse orderTest(Long doctorId, OrderTestRequest req) {
        MedicalTest test = MedicalTest.builder()
                .patientId(req.getPatientId())
                .doctorId(doctorId)
                .prescriptionId(req.getPrescriptionId())
                .testName(req.getTestName())
                .testType(req.getTestType())
                .build();

        MedicalTest saved = medicalTestRepository.save(test);

        eventPublisher.publishTestOrdered(new MedicalEvents.TestOrderedEvent(
                saved.getId(),
                saved.getPatientId(),
                saved.getDoctorId(),
                saved.getTestName(),
                saved.getTestType()
        ));

        log.info("Test {} ordered by doctorId={}", saved.getId(), doctorId);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalTestResponse getTest(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<MedicalTestResponse> getTestsForPatient(Long patientId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("orderedAt").descending());
        return PagedResponse.from(medicalTestRepository.findByPatientId(patientId, pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<MedicalTestResponse> getTestsByDoctor(Long doctorId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("orderedAt").descending());
        return PagedResponse.from(medicalTestRepository.findByDoctorId(doctorId, pageable).map(this::toResponse));
    }

    @Override
    @Transactional
    public MedicalTestResponse updateResult(Long id, UpdateTestResultRequest req) {
        MedicalTest test = findById(id);
        if (test.getStatus() == TestStatus.COMPLETED) {
            throw new BadRequestException("Test result already submitted");
        }

        test.setResultValue(req.getResultValue());
        test.setIsAbnormal(req.isAbnormal());
        test.setLabNotes(req.getLabNotes());
        test.setResultDate(Instant.now());
        test.setStatus(TestStatus.COMPLETED);

        MedicalTest saved = medicalTestRepository.save(test);

        eventPublisher.publishTestResultUploaded(new MedicalEvents.TestResultUploadedEvent(
                saved.getId(),
                saved.getPatientId(),
                saved.getTestName(),
                saved.getResultValue(),
                saved.getIsAbnormal()
        ));

        log.info("Result uploaded for testId={}", saved.getId());
        return toResponse(saved);
    }

    private MedicalTest findById(Long id) {
        return medicalTestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medical test not found: " + id));
    }

    private MedicalTestResponse toResponse(MedicalTest t) {
        return MedicalTestResponse.builder()
                .id(t.getId())
                .uuid(t.getUuid())
                .patientId(t.getPatientId())
                .doctorId(t.getDoctorId())
                .prescriptionId(t.getPrescriptionId())
                .testName(t.getTestName())
                .testType(t.getTestType())
                .status(t.getStatus())
                .orderedAt(t.getOrderedAt())
                .resultDate(t.getResultDate())
                .resultValue(t.getResultValue())
                .isAbnormal(t.getIsAbnormal())
                .labNotes(t.getLabNotes())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
