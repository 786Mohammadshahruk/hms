package com.hms.billing.scheduler;

import com.hms.billing.entity.Bill;
import com.hms.billing.enums.BillStatus;
import com.hms.billing.kafka.BillingEventPublisher;
import com.hms.billing.kafka.BillingEvents;
import com.hms.billing.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingReminderScheduler {

    private final BillRepository        billRepository;
    private final BillingEventPublisher eventPublisher;

    /**
     * Every day at 7 AM: mark overdue bills and publish events.
     */
    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void markOverdueBills() {
        LocalDate today = LocalDate.now();
        List<Bill> overdueBills = billRepository.findOverdueBills(today);

        if (overdueBills.isEmpty()) {
            log.info("No overdue bills found");
            return;
        }

        log.info("Found {} overdue bills", overdueBills.size());

        overdueBills.forEach(bill -> {
            bill.setStatus(BillStatus.OVERDUE);
            billRepository.save(bill);

            eventPublisher.publishPaymentOverdue(new BillingEvents.PaymentOverdueEvent(
                    bill.getId(),
                    bill.getPatientId(),
                    bill.getTotalAmount().subtract(bill.getPaidAmount()),
                    bill.getDueDate().toString()
            ));

            log.info("Bill {} marked as OVERDUE for patientId={}", bill.getId(), bill.getPatientId());
        });
    }

    /**
     * Every day at 8 AM: send reminders for bills due tomorrow.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDueTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Bill> dueTomorrow = billRepository.findBillsDueOn(tomorrow);

        if (dueTomorrow.isEmpty()) {
            log.info("No bills due tomorrow");
            return;
        }

        log.info("{} bills due tomorrow — reminders would be sent via notification-service", dueTomorrow.size());
        dueTomorrow.forEach(bill ->
            log.info("Reminder: Bill {} due on {} for patientId={} amount={}",
                    bill.getId(), bill.getDueDate(), bill.getPatientId(),
                    bill.getTotalAmount().subtract(bill.getPaidAmount()))
        );
    }
}
