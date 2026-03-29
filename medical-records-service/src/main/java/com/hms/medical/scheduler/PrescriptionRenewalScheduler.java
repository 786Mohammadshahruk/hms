package com.hms.medical.scheduler;

import com.hms.medical.entity.Prescription;
import com.hms.medical.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionRenewalScheduler {

    private final PrescriptionRepository prescriptionRepository;

    /**
     * Every day at 9 AM: log prescriptions expiring in the next 3 days.
     * Notification is handled by the notification-service via Kafka events.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void checkExpiringPrescriptions() {
        LocalDate today    = LocalDate.now();
        LocalDate in3Days  = today.plusDays(3);

        List<Prescription> expiring = prescriptionRepository.findExpiringPrescriptions(today, in3Days);

        if (expiring.isEmpty()) {
            log.info("No prescriptions expiring in the next 3 days");
            return;
        }

        log.info("Found {} prescriptions expiring between {} and {}", expiring.size(), today, in3Days);
        expiring.forEach(p ->
            log.info("Prescription id={} for patientId={} expires on {}", p.getId(), p.getPatientId(), p.getValidUntil())
        );
    }
}
