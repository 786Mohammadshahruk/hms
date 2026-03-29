package com.hms.medical.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTestRequest {

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    private Long prescriptionId;

    @NotBlank(message = "Test name is required")
    private String testName;

    @NotBlank(message = "Test type is required")
    private String testType;
}
