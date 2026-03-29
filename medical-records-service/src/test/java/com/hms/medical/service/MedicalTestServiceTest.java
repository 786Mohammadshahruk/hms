package com.hms.medical.service;

import com.hms.medical.dto.request.OrderTestRequest;
import com.hms.medical.dto.request.UpdateTestResultRequest;
import com.hms.medical.dto.response.MedicalTestResponse;
import com.hms.medical.entity.MedicalTest;
import com.hms.medical.enums.TestStatus;
import com.hms.medical.exception.BadRequestException;
import com.hms.medical.exception.ResourceNotFoundException;
import com.hms.medical.kafka.MedicalEventPublisher;
import com.hms.medical.repository.MedicalTestRepository;
import com.hms.medical.service.impl.MedicalTestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicalTestService Tests")
class MedicalTestServiceTest {

    @Mock MedicalTestRepository medicalTestRepository;
    @Mock MedicalEventPublisher eventPublisher;

    @InjectMocks MedicalTestServiceImpl medicalTestService;

    private MedicalTest pendingTest;

    @BeforeEach
    void setUp() {
        pendingTest = new MedicalTest();
        pendingTest.setId(1L);
        pendingTest.setUuid(UUID.randomUUID());
        pendingTest.setPatientId(10L);
        pendingTest.setDoctorId(20L);
        pendingTest.setPrescriptionId(5L);
        pendingTest.setTestName("Complete Blood Count");
        pendingTest.setTestType("HAEMATOLOGY");
        pendingTest.setStatus(TestStatus.ORDERED);
    }

    // ── orderTest ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("orderTest()")
    class OrderTestTests {

        @Test
        @DisplayName("Should save test and publish Kafka event on success")
        void orderTest_success() {
            OrderTestRequest req = new OrderTestRequest(10L, 5L, "Complete Blood Count", "HAEMATOLOGY");

            when(medicalTestRepository.save(any())).thenReturn(pendingTest);

            MedicalTestResponse response = medicalTestService.orderTest(20L, req);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getPatientId()).isEqualTo(10L);
            assertThat(response.getDoctorId()).isEqualTo(20L);
            assertThat(response.getTestName()).isEqualTo("Complete Blood Count");
            assertThat(response.getStatus()).isEqualTo(TestStatus.ORDERED);
            verify(medicalTestRepository).save(any());
            verify(eventPublisher).publishTestOrdered(any());
        }

        @Test
        @DisplayName("Should set doctorId from the authenticated doctor, not from request")
        void orderTest_usesDoctorIdFromParam() {
            OrderTestRequest req = new OrderTestRequest(10L, null, "Urine Analysis", "PATHOLOGY");

            MedicalTest saved = new MedicalTest();
            saved.setId(2L);
            saved.setPatientId(10L);
            saved.setDoctorId(99L); // authenticated doctor
            saved.setTestName("Urine Analysis");
            saved.setTestType("PATHOLOGY");
            saved.setStatus(TestStatus.ORDERED);

            when(medicalTestRepository.save(any())).thenReturn(saved);

            MedicalTestResponse response = medicalTestService.orderTest(99L, req);

            assertThat(response.getDoctorId()).isEqualTo(99L);
        }
    }

    // ── getTest ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTest()")
    class GetTestTests {

        @Test
        @DisplayName("Should return test when found")
        void getTest_success() {
            when(medicalTestRepository.findById(1L)).thenReturn(Optional.of(pendingTest));

            MedicalTestResponse response = medicalTestService.getTest(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getTestName()).isEqualTo("Complete Blood Count");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when test not found")
        void getTest_notFound_throws() {
            when(medicalTestRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> medicalTestService.getTest(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
        }
    }

    // ── updateResult ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateResult()")
    class UpdateResultTests {

        @Test
        @DisplayName("Should update test to COMPLETED and publish result event")
        void updateResult_success() {
            UpdateTestResultRequest req = new UpdateTestResultRequest();
            req.setResultValue("Normal — 5.2 million RBC/µL");
            req.setAbnormal(false);
            req.setLabNotes("Within reference range");

            MedicalTest completed = new MedicalTest();
            completed.setId(1L);
            completed.setPatientId(10L);
            completed.setDoctorId(20L);
            completed.setTestName("Complete Blood Count");
            completed.setTestType("HAEMATOLOGY");
            completed.setStatus(TestStatus.COMPLETED);
            completed.setResultValue("Normal — 5.2 million RBC/µL");
            completed.setIsAbnormal(false);
            completed.setLabNotes("Within reference range");

            when(medicalTestRepository.findById(1L)).thenReturn(Optional.of(pendingTest));
            when(medicalTestRepository.save(any())).thenReturn(completed);

            MedicalTestResponse response = medicalTestService.updateResult(1L, req);

            assertThat(response.getStatus()).isEqualTo(TestStatus.COMPLETED);
            assertThat(response.getResultValue()).isEqualTo("Normal — 5.2 million RBC/µL");
            assertThat(response.getIsAbnormal()).isFalse();
            verify(eventPublisher).publishTestResultUploaded(any());
        }

        @Test
        @DisplayName("Should mark test as abnormal when result is abnormal")
        void updateResult_abnormal_success() {
            UpdateTestResultRequest req = new UpdateTestResultRequest();
            req.setResultValue("High — 12.5 g/dL WBC");
            req.setAbnormal(true);
            req.setLabNotes("Elevated WBC count — recommend follow-up");

            MedicalTest completed = new MedicalTest();
            completed.setId(1L);
            completed.setPatientId(10L);
            completed.setStatus(TestStatus.COMPLETED);
            completed.setResultValue("High — 12.5 g/dL WBC");
            completed.setIsAbnormal(true);

            when(medicalTestRepository.findById(1L)).thenReturn(Optional.of(pendingTest));
            when(medicalTestRepository.save(any())).thenReturn(completed);

            MedicalTestResponse response = medicalTestService.updateResult(1L, req);

            assertThat(response.getIsAbnormal()).isTrue();
        }

        @Test
        @DisplayName("Should throw BadRequestException when result already submitted")
        void updateResult_alreadyCompleted_throws() {
            pendingTest.setStatus(TestStatus.COMPLETED);
            UpdateTestResultRequest req = new UpdateTestResultRequest();
            req.setResultValue("Some value");

            when(medicalTestRepository.findById(1L)).thenReturn(Optional.of(pendingTest));

            assertThatThrownBy(() -> medicalTestService.updateResult(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already submitted");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when test not found")
        void updateResult_testNotFound_throws() {
            UpdateTestResultRequest req = new UpdateTestResultRequest();
            req.setResultValue("Result");
            when(medicalTestRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> medicalTestService.updateResult(999L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
        }
    }
}
