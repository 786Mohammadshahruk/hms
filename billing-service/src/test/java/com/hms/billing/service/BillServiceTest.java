package com.hms.billing.service;

import com.hms.billing.dto.request.BillItemRequest;
import com.hms.billing.dto.request.CreateBillRequest;
import com.hms.billing.dto.response.BillResponse;
import com.hms.billing.entity.Bill;
import com.hms.billing.entity.BillItem;
import com.hms.billing.enums.BillStatus;
import com.hms.billing.exception.BadRequestException;
import com.hms.billing.exception.ResourceNotFoundException;
import com.hms.billing.kafka.BillingEventPublisher;
import com.hms.billing.repository.BillRepository;
import com.hms.billing.service.impl.BillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillServiceTest {

    @Mock  BillRepository        billRepository;
    @Mock  BillingEventPublisher eventPublisher;
    @InjectMocks BillServiceImpl billService;

    private CreateBillRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateBillRequest(
                1L, null, "Consultation fee", null,
                List.of(new BillItemRequest("Consultation", new BigDecimal("500.00"), 1))
        );
    }

    @Test
    void createBill_success() {
        Bill saved = buildBill(1L, 1L, new BigDecimal("500.00"), BillStatus.PENDING);
        when(billRepository.save(any())).thenReturn(saved);

        BillResponse response = billService.createBill(validRequest);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(BillStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        verify(eventPublisher, times(1)).publishBillGenerated(any());
    }

    @Test
    void getBill_notFound() {
        when(billRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> billService.getBill(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void cancelBill_success() {
        Bill bill = buildBill(1L, 1L, new BigDecimal("500.00"), BillStatus.PENDING);
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BillResponse response = billService.cancelBill(1L);

        assertThat(response.getStatus()).isEqualTo(BillStatus.CANCELLED);
    }

    @Test
    void cancelBill_alreadyPaid_throws() {
        Bill bill = buildBill(1L, 1L, new BigDecimal("500.00"), BillStatus.PAID);
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> billService.cancelBill(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("paid bill");
    }

    @Test
    void cancelBill_alreadyCancelled_throws() {
        Bill bill = buildBill(1L, 1L, new BigDecimal("500.00"), BillStatus.CANCELLED);
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> billService.cancelBill(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already cancelled");
    }

    private Bill buildBill(Long id, Long patientId, BigDecimal total, BillStatus status) {
        Bill bill = new Bill();
        bill.setId(id);
        bill.setPatientId(patientId);
        bill.setTotalAmount(total);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setStatus(status);

        List<BillItem> items = new ArrayList<>();
        BillItem item = new BillItem();
        item.setId(1L);
        item.setDescription("Consultation");
        item.setUnitPrice(total);
        item.setQuantity(1);
        item.setTotalPrice(total);
        item.setBill(bill);
        items.add(item);
        bill.setItems(items);
        bill.setPayments(new ArrayList<>());
        return bill;
    }
}
