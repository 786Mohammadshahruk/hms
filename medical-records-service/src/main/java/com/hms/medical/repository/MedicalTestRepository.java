package com.hms.medical.repository;

import com.hms.medical.entity.MedicalTest;
import com.hms.medical.enums.TestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicalTestRepository extends JpaRepository<MedicalTest, Long> {

    Optional<MedicalTest> findByUuid(UUID uuid);

    Page<MedicalTest> findByPatientId(Long patientId, Pageable pageable);

    Page<MedicalTest> findByPatientIdAndStatus(Long patientId, TestStatus status, Pageable pageable);

    Page<MedicalTest> findByDoctorId(Long doctorId, Pageable pageable);
}
