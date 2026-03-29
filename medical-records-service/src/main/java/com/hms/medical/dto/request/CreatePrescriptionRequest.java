package com.hms.medical.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePrescriptionRequest {

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    private Long appointmentId;

    @NotBlank(message = "Diagnosis is required")
    private String diagnosis;

    private String    notes;
    private LocalDate validUntil;

    @NotEmpty(message = "At least one medicine is required")
    @Valid
    private List<AddMedicineRequest> medicines;
}
