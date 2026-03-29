package com.hms.billing.service.impl;

import com.hms.billing.dto.request.CreateBillRequest;
import com.hms.billing.dto.response.BillItemResponse;
import com.hms.billing.dto.response.BillResponse;
import com.hms.billing.dto.response.PagedResponse;
import com.hms.billing.entity.Bill;
import com.hms.billing.entity.BillItem;
import com.hms.billing.enums.BillStatus;
import com.hms.billing.exception.BadRequestException;
import com.hms.billing.exception.ResourceNotFoundException;
import com.hms.billing.kafka.BillingEventPublisher;
import com.hms.billing.kafka.BillingEvents;
import com.hms.billing.repository.BillRepository;
import com.hms.billing.service.BillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillService {

    private static final DateTimeFormatter BILL_NUM_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BillRepository        billRepository;
    private final BillingEventPublisher eventPublisher;

    @Override
    @Transactional
    @CacheEvict(value = "bills", allEntries = true)
    public BillResponse createBill(CreateBillRequest req) {
        String billNumber = "BILL-" + LocalDateTime.now().format(BILL_NUM_FMT)
                + "-" + req.getPatientId();

        Bill bill = Bill.builder()
                .billNumber(billNumber)
                .patientId(req.getPatientId())
                .appointmentId(req.getAppointmentId())
                .description(req.getDescription())
                .dueDate(req.getDueDate())
                .totalAmount(BigDecimal.ZERO)
                .build();

        List<BillItem> items = req.getItems().stream()
                .map(i -> {
                    int qty = i.getQuantity() != null ? i.getQuantity() : 1;
                    return BillItem.builder()
                            .bill(bill)
                            .description(i.getDescription())
                            .unitPrice(i.getUnitPrice())
                            .quantity(qty)
                            .totalPrice(i.getUnitPrice().multiply(BigDecimal.valueOf(qty)))
                            .build();
                })
                .toList();

        bill.getItems().addAll(items);

        BigDecimal total = items.stream()
                .map(BillItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        bill.setTotalAmount(total);

        Bill saved = billRepository.save(bill);

        eventPublisher.publishBillGenerated(new BillingEvents.BillGeneratedEvent(
                saved.getId(),
                saved.getPatientId(),
                saved.getTotalAmount(),
                saved.getDueDate() != null ? saved.getDueDate().toString() : null
        ));

        log.info("Bill {} ({}) created for patientId={} total={}", saved.getId(), billNumber, saved.getPatientId(), total);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "bills", key = "#id")
    public BillResponse getBill(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<BillResponse> getBillsForPatient(Long patientId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.from(billRepository.findByPatientId(patientId, pageable).map(this::toResponse));
    }

    @Override
    @Transactional
    @CacheEvict(value = "bills", key = "#id")
    public BillResponse cancelBill(Long id) {
        Bill bill = findById(id);
        if (bill.getStatus() == BillStatus.PAID) {
            throw new BadRequestException("Cannot cancel a paid bill");
        }
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new BadRequestException("Bill is already cancelled");
        }
        bill.setStatus(BillStatus.CANCELLED);
        return toResponse(billRepository.save(bill));
    }

    private Bill findById(Long id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + id));
    }

    private BillResponse toResponse(Bill b) {
        List<BillItemResponse> itemResponses = b.getItems().stream()
                .map(i -> BillItemResponse.builder()
                        .id(i.getId())
                        .description(i.getDescription())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .totalPrice(i.getTotalPrice())
                        .build())
                .toList();

        BigDecimal remaining = b.getTotalAmount().subtract(b.getPaidAmount());

        return BillResponse.builder()
                .id(b.getId())
                .uuid(b.getUuid())
                .patientId(b.getPatientId())
                .appointmentId(b.getAppointmentId())
                .totalAmount(b.getTotalAmount())
                .paidAmount(b.getPaidAmount())
                .remainingAmount(remaining)
                .dueDate(b.getDueDate())
                .status(b.getStatus())
                .description(b.getDescription())
                .items(itemResponses)
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}
