package com.hms.billing.repository;

import com.hms.billing.entity.Bill;
import com.hms.billing.enums.BillStatus;
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
public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByUuid(UUID uuid);

    Page<Bill> findByPatientId(Long patientId, Pageable pageable);

    Page<Bill> findByPatientIdAndStatus(Long patientId, BillStatus status, Pageable pageable);

    @Query("SELECT b FROM Bill b WHERE b.status IN ('PENDING','PARTIAL') AND b.dueDate < :today")
    List<Bill> findOverdueBills(@Param("today") LocalDate today);

    @Query("SELECT b FROM Bill b WHERE b.status IN ('PENDING','PARTIAL') AND b.dueDate = :tomorrow")
    List<Bill> findBillsDueOn(@Param("tomorrow") LocalDate tomorrow);
}
