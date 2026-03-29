package com.hms.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.user.enums.Gender;
import com.hms.user.enums.Role;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Public-facing user representation — never exposes passwordHash.
 * Doctor/patient profile fields are included only when populated.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    // Core user fields
    private final Long      id;
    private final UUID      uuid;
    private final String    firstName;
    private final String    lastName;
    private final String    fullName;
    private final String    email;
    private final String    phone;
    private final Role      role;
    private final Gender    gender;
    private final LocalDate dateOfBirth;
    private final String    address;
    private final boolean   active;
    private final boolean   emailVerified;
    private final Instant   createdAt;
    private final Instant   updatedAt;

    // Doctor profile (populated when role = DOCTOR)
    private final String     specialization;
    private final String     licenseNumber;
    private final Integer    experienceYears;
    private final String     department;
    private final BigDecimal consultationFee;
    private final LocalTime  availableFrom;
    private final LocalTime  availableTo;

    // Patient profile (populated when role = PATIENT)
    private final String bloodGroup;
    private final String emergencyContact;
    private final String allergies;
    private final String chronicConditions;
    private final String insuranceProvider;
    private final String insuranceNumber;
}
