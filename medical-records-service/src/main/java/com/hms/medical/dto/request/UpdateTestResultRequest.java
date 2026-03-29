package com.hms.medical.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTestResultRequest {

    @NotBlank(message = "Result value is required")
    private String  resultValue;

    private boolean isAbnormal;
    private String  labNotes;
}
