package com.hms.user.mapper;

import com.hms.user.dto.response.UserResponse;
import com.hms.user.entity.DoctorProfile;
import com.hms.user.entity.PatientProfile;
import com.hms.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct mapper — converts User entity (+ optional profiles) to UserResponse DTO.
 * Never exposes passwordHash in output.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Maps a User with no extended profile.
     */
    @Mapping(target = "fullName",         expression = "java(user.getFullName())")
    @Mapping(target = "specialization",   ignore = true)
    @Mapping(target = "licenseNumber",    ignore = true)
    @Mapping(target = "experienceYears",  ignore = true)
    @Mapping(target = "department",       ignore = true)
    @Mapping(target = "consultationFee",  ignore = true)
    @Mapping(target = "availableFrom",    ignore = true)
    @Mapping(target = "availableTo",      ignore = true)
    @Mapping(target = "bloodGroup",       ignore = true)
    @Mapping(target = "emergencyContact", ignore = true)
    @Mapping(target = "allergies",        ignore = true)
    @Mapping(target = "chronicConditions",ignore = true)
    @Mapping(target = "insuranceProvider",ignore = true)
    @Mapping(target = "insuranceNumber",  ignore = true)
    UserResponse toResponse(User user);

    /**
     * Maps a DOCTOR user with their profile merged into the response.
     */
    default UserResponse toResponseWithDoctorProfile(User user, DoctorProfile profile) {
        return UserResponse.builder()
            .id(user.getId())
            .uuid(user.getUuid())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .fullName(user.getFullName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .role(user.getRole())
            .gender(user.getGender())
            .dateOfBirth(user.getDateOfBirth())
            .address(user.getAddress())
            .active(user.isActive())
            .emailVerified(user.isEmailVerified())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            // doctor fields
            .specialization(profile != null ? profile.getSpecialization()  : null)
            .licenseNumber(profile  != null ? profile.getLicenseNumber()   : null)
            .experienceYears(profile != null ? profile.getExperienceYears() : null)
            .department(profile     != null ? profile.getDepartment()       : null)
            .consultationFee(profile != null ? profile.getConsultationFee() : null)
            .availableFrom(profile  != null ? profile.getAvailableFrom()   : null)
            .availableTo(profile    != null ? profile.getAvailableTo()     : null)
            .build();
    }

    /**
     * Maps a PATIENT user with their profile merged into the response.
     */
    default UserResponse toResponseWithPatientProfile(User user, PatientProfile profile) {
        return UserResponse.builder()
            .id(user.getId())
            .uuid(user.getUuid())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .fullName(user.getFullName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .role(user.getRole())
            .gender(user.getGender())
            .dateOfBirth(user.getDateOfBirth())
            .address(user.getAddress())
            .active(user.isActive())
            .emailVerified(user.isEmailVerified())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            // patient fields
            .bloodGroup(profile       != null ? profile.getBloodGroup()       : null)
            .emergencyContact(profile != null ? profile.getEmergencyContact() : null)
            .allergies(profile        != null ? profile.getAllergies()         : null)
            .chronicConditions(profile != null ? profile.getChronicConditions(): null)
            .insuranceProvider(profile != null ? profile.getInsuranceProvider(): null)
            .insuranceNumber(profile  != null ? profile.getInsuranceNumber()  : null)
            .build();
    }
}
