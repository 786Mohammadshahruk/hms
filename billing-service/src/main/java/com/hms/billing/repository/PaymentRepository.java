package com.hms.billing.repository;

import com.hms.billing.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByUuid(UUID uuid);

    Page<Payment> findByBillId(Long billId, Pageable pageable);

    /** Find all payments for a patient by traversing the bill relationship. */
    Page<Payment> findByBill_PatientId(Long patientId, Pageable pageable);
}
