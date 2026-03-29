package com.hms.medical.repository;

import com.hms.medical.entity.Prescription;
import com.hms.medical.enums.PrescriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Optional<Prescription> findByUuid(UUID uuid);

    Page<Prescription> findByPatientId(Long patientId, Pageable pageable);

    Page<Prescription> findByDoctorId(Long doctorId, Pageable pageable);

    Page<Prescription> findByPatientIdAndStatus(Long patientId, PrescriptionStatus status, Pageable pageable);

    @Query("SELECT p FROM Prescription p WHERE p.status = 'ACTIVE' AND p.validUntil BETWEEN :from AND :to")
    List<Prescription> findExpiringPrescriptions(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
