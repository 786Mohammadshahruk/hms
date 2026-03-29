package com.hms.user.dto.request;

import com.hms.user.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

// ── Login ──────────────────────────────────────────────────────────────────────

@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
