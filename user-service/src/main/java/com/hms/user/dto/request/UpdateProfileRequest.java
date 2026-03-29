package com.hms.user.dto.request;

import com.hms.user.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class UpdateProfileRequest {

    @Size(min = 2, max = 100)
    private String firstName;

    @Size(min = 2, max = 100)
    private String lastName;

    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone must be a valid number")
    private String phone;

    private Gender gender;
    private LocalDate dateOfBirth;
    private String address;

    // Doctor fields
    private String specialization;
    private Integer experienceYears;
    private String department;
    private BigDecimal consultationFee;
    private LocalTime availableFrom;
    private LocalTime availableTo;

    // Patient fields
    private String bloodGroup;
    private String emergencyContact;
    private String allergies;
    private String chronicConditions;
    private String insuranceProvider;
    private String insuranceNumber;
}
