package com.hms.user.dto.request;

import com.hms.user.enums.Gender;
import com.hms.user.enums.Role;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request body for admin-only user creation.
 * Allows creating any role: PATIENT, DOCTOR, CASHIER, or ADMIN.
 * Role-specific fields (specialization etc.) are required when role = DOCTOR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreateUserRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    private String email;

    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone must be a valid number")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72)
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain uppercase, lowercase, digit and special character"
    )
    private String password;

    @NotNull(message = "Role is required")
    private Role role;

    private Gender    gender;
    private LocalDate dateOfBirth;
    private String    address;

    // ── Doctor-specific (required when role = DOCTOR) ──────────────────────────
    private String     specialization;
    private String     licenseNumber;
    private Integer    experienceYears;
    private String     department;
    private BigDecimal consultationFee;
    private LocalTime  availableFrom;
    private LocalTime  availableTo;

    // ── Patient-specific (optional when role = PATIENT) ────────────────────────
    private String bloodGroup;
    private String emergencyContact;
    private String allergies;
    private String chronicConditions;
    private String insuranceProvider;
    private String insuranceNumber;
}
