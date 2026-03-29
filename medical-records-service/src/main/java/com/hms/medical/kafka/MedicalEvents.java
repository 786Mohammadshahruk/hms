package com.hms.medical.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class MedicalEvents {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrescriptionCreatedEvent {
        private Long        prescriptionId;
        private Long        patientId;
        private Long        doctorId;
        private String      diagnosis;
        private List<String> medicineNames;
        private LocalDate   validUntil;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestOrderedEvent {
        private Long   testId;
        private Long   patientId;
        private Long   doctorId;
        private String testName;
        private String testType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestResultUploadedEvent {
        private Long    testId;
        private Long    patientId;
        private String  testName;
        private String  resultValue;
        private boolean abnormal;
    }
}
