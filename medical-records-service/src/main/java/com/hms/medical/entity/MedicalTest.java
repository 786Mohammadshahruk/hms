package com.hms.medical.entity;

import com.hms.medical.enums.TestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medical_tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalTest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @UuidGenerator
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Column(name = "test_name", nullable = false, length = 200)
    private String testName;

    @Column(name = "test_type", nullable = false, length = 100)
    private String testType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private TestStatus status = TestStatus.ORDERED;

    @Builder.Default
    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt = Instant.now();

    @Column(name = "result_date")
    private Instant resultDate;

    @Column(name = "result_value", columnDefinition = "TEXT")
    private String resultValue;

    @Builder.Default
    @Column(name = "is_abnormal")
    private Boolean isAbnormal = false;

    @Column(name = "lab_notes", columnDefinition = "TEXT")
    private String labNotes;
}
