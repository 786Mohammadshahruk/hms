package com.hms.medical.service;

import com.hms.medical.dto.request.AddMedicineRequest;
import com.hms.medical.dto.request.CreatePrescriptionRequest;
import com.hms.medical.dto.response.PrescriptionResponse;
import com.hms.medical.entity.Prescription;
import com.hms.medical.entity.PrescriptionMedicine;
import com.hms.medical.enums.PrescriptionStatus;
import com.hms.medical.exception.BadRequestException;
import com.hms.medical.exception.ResourceNotFoundException;
import com.hms.medical.kafka.MedicalEventPublisher;
import com.hms.medical.repository.PrescriptionRepository;
import com.hms.medical.service.impl.PrescriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    @Mock  PrescriptionRepository  prescriptionRepository;
    @Mock  MedicalEventPublisher   eventPublisher;
    @InjectMocks PrescriptionServiceImpl prescriptionService;

    private CreatePrescriptionRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = CreatePrescriptionRequest.builder()
                .patientId(10L)
                .diagnosis("Fever")
                .medicines(List.of(new AddMedicineRequest("Paracetamol", "500mg", "3x daily", 5, null, 15)))
                .build();
    }

    @Test
    void createPrescription_success() {
        Prescription saved = buildPrescription(1L, 10L, 20L, PrescriptionStatus.ACTIVE);
        when(prescriptionRepository.save(any())).thenReturn(saved);

        PrescriptionResponse response = prescriptionService.createPrescription(20L, validRequest);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        verify(eventPublisher, times(1)).publishPrescriptionCreated(any());
    }

    @Test
    void getPrescription_notFound() {
        when(prescriptionRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> prescriptionService.getPrescription(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void cancelPrescription_success() {
        Prescription prescription = buildPrescription(1L, 10L, 20L, PrescriptionStatus.ACTIVE);
        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PrescriptionResponse response = prescriptionService.cancelPrescription(1L, 20L);

        assertThat(response.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);
    }

    @Test
    void cancelPrescription_wrongDoctor_throws() {
        Prescription prescription = buildPrescription(1L, 10L, 20L, PrescriptionStatus.ACTIVE);
        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> prescriptionService.cancelPrescription(1L, 99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    void cancelPrescription_alreadyCancelled_throws() {
        Prescription prescription = buildPrescription(1L, 10L, 20L, PrescriptionStatus.CANCELLED);
        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> prescriptionService.cancelPrescription(1L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already cancelled");
    }

    private Prescription buildPrescription(Long id, Long patientId, Long doctorId, PrescriptionStatus status) {
        Prescription p = new Prescription();
        p.setId(id);
        p.setPatientId(patientId);
        p.setDoctorId(doctorId);
        p.setDiagnosis("Fever");
        p.setStatus(status);
        p.setValidUntil(LocalDate.now().plusDays(30));
        List<PrescriptionMedicine> medicines = new ArrayList<>();
        PrescriptionMedicine m = new PrescriptionMedicine();
        m.setId(1L);
        m.setMedicineName("Paracetamol");
        m.setDosage("500mg");
        m.setFrequency("3x daily");
        m.setQuantity(15);
        m.setPrescription(p);
        medicines.add(m);
        p.setMedicines(medicines);
        return p;
    }
}
