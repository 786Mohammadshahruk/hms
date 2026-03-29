package com.hms.billing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBillRequest {

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    private Long appointmentId;

    private String description;
    private LocalDate dueDate;

    @NotEmpty(message = "At least one bill item is required")
    @Valid
    private List<BillItemRequest> items;
}
