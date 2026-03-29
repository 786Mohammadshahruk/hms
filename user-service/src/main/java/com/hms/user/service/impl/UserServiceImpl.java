package com.hms.user.service.impl;

import com.hms.user.dto.request.AdminCreateUserRequest;
import com.hms.user.dto.request.ChangePasswordRequest;
import com.hms.user.dto.request.ChangeRoleRequest;
import com.hms.user.dto.request.UpdateProfileRequest;
import com.hms.user.dto.response.PagedResponse;
import com.hms.user.dto.response.UserResponse;
import com.hms.user.entity.DoctorProfile;
import com.hms.user.entity.PatientProfile;
import com.hms.user.entity.User;
import com.hms.user.enums.Role;
import com.hms.user.exception.BadRequestException;
import com.hms.user.exception.ResourceNotFoundException;
import com.hms.user.kafka.UserEventPublisher;
import com.hms.user.kafka.UserEvents;
import com.hms.user.mapper.UserMapper;
import com.hms.user.repository.DoctorProfileRepository;
import com.hms.user.repository.PatientProfileRepository;
import com.hms.user.repository.UserRepository;
import com.hms.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository           userRepository;
    private final DoctorProfileRepository  doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final PasswordEncoder          passwordEncoder;
    private final UserMapper               userMapper;
    private final UserEventPublisher       eventPublisher;

    // ── Queries ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = findUserById(id);
        return buildFullResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getByUuid(UUID uuid) {
        User user = userRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("User", "uuid", uuid));
        return buildFullResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMyProfile(Long authenticatedUserId) {
        return getById(authenticatedUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getAllUsers(String search, Role role,
                                                   Boolean active, Pageable pageable) {
        Page<UserResponse> page;
        if (search != null && !search.isBlank()) {
            page = userRepository
                .searchUsers(search.trim(), role, active, pageable)
                .map(this::buildFullResponse);
        } else if (role != null) {
            page = userRepository.findAllByRole(role, pageable)
                .map(this::buildFullResponse);
        } else {
            page = userRepository.findAllByActiveTrue(pageable)
                .map(this::buildFullResponse);
        }
        return PagedResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getDoctors(String specialization,
                                                   String department,
                                                   Pageable pageable) {
        Page<UserResponse> page = doctorProfileRepository
            .findBySpecializationAndDepartment(specialization, department, pageable)
            .map(dp -> userMapper.toResponseWithDoctorProfile(dp.getUser(), dp));
        return PagedResponse.from(page);
    }

    // ── Commands ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse updateProfile(Long authenticatedUserId, UpdateProfileRequest req) {
        User user = findUserById(authenticatedUserId);

        // Apply non-null fields (functional style — pure updates)
        Optional.ofNullable(req.getFirstName()).ifPresent(user::setFirstName);
        Optional.ofNullable(req.getLastName()).ifPresent(user::setLastName);
        Optional.ofNullable(req.getPhone()).ifPresent(user::setPhone);
        Optional.ofNullable(req.getGender()).ifPresent(user::setGender);
        Optional.ofNullable(req.getDateOfBirth()).ifPresent(user::setDateOfBirth);
        Optional.ofNullable(req.getAddress()).ifPresent(user::setAddress);

        user = userRepository.save(user);

        UserResponse response = switch (user.getRole()) {
            case DOCTOR  -> updateDoctorProfile(user, req);
            case PATIENT -> updatePatientProfile(user, req);
            default      -> userMapper.toResponse(user);
        };

        // Publish async event
        final Long   userId   = user.getId();
        final String email    = user.getEmail();
        final String fullName = user.getFullName();
        eventPublisher.publishUserUpdated(UserEvents.UserUpdatedEvent.builder()
            .userId(userId).email(email).fullName(fullName)
            .occurredAt(Instant.now())
            .build());

        log.info("Profile updated for userId={}", userId);
        return response;
    }

    @Override
    @Transactional
    public void changePassword(Long authenticatedUserId, ChangePasswordRequest req) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }

        User user = findUserById(authenticatedUserId);

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for userId={}", authenticatedUserId);
    }

    @Override
    @Transactional
    public void deactivateUser(Long targetUserId, Long requestingUserId) {
        if (targetUserId.equals(requestingUserId)) {
            throw new BadRequestException("You cannot deactivate your own account");
        }
        User user = findUserById(targetUserId);
        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated: targetId={} by requestingId={}", targetUserId, requestingUserId);
    }

    @Override
    @Transactional
    public void activateUser(Long targetUserId) {
        User user = findUserById(targetUserId);
        user.setActive(true);
        userRepository.save(user);
        log.info("User activated: userId={}", targetUserId);
    }

    // ── Admin use cases ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse adminCreateUser(AdminCreateUserRequest request, Long adminId) {
        log.info("Admin {} creating user with email={} role={}", adminId,
            request.getEmail(), request.getRole());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered: " + request.getEmail());
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Phone already registered: " + request.getPhone());
        }
        if (request.getRole() == Role.DOCTOR) {
            if (request.getSpecialization() == null || request.getSpecialization().isBlank()) {
                throw new BadRequestException("Specialization is required for DOCTOR role");
            }
            if (request.getLicenseNumber() == null || request.getLicenseNumber().isBlank()) {
                throw new BadRequestException("License number is required for DOCTOR role");
            }
        }

        User user = User.builder()
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail().toLowerCase())
            .phone(request.getPhone())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(request.getRole())
            .gender(request.getGender())
            .dateOfBirth(request.getDateOfBirth())
            .address(request.getAddress())
            .build();

        user = userRepository.save(user);

        UserResponse response = switch (request.getRole()) {
            case DOCTOR  -> createDoctorProfileFromAdmin(user, request);
            case PATIENT -> createPatientProfileFromAdmin(user, request);
            default      -> userMapper.toResponse(user);
        };

        final Long userId = user.getId();
        final Role role   = user.getRole();
        eventPublisher.publishUserRegistered(UserEvents.UserRegisteredEvent.builder()
            .userId(userId).email(user.getEmail())
            .fullName(user.getFullName()).role(role)
            .occurredAt(Instant.now())
            .build());

        log.info("Admin {} created user id={} role={}", adminId, userId, role);
        return response;
    }

    @Override
    @Transactional
    public UserResponse changeUserRole(Long targetUserId, ChangeRoleRequest request, Long adminId) {
        User user = findUserById(targetUserId);
        Role oldRole = user.getRole();
        Role newRole = request.getNewRole();

        if (oldRole == newRole) {
            throw new BadRequestException("User already has role: " + newRole);
        }

        user.setRole(newRole);
        user = userRepository.save(user);

        log.info("Admin {} changed role for userId={} from {} to {}. Reason: {}",
            adminId, targetUserId, oldRole, newRole, request.getReason());

        return buildFullResponse(user);
    }

    @Override
    @Transactional
    public void adminResetPassword(Long targetUserId, String newPassword, Long adminId) {
        User user = findUserById(targetUserId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Admin {} reset password for userId={}", adminId, targetUserId);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private User findUserById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private UserResponse buildFullResponse(User user) {
        return switch (user.getRole()) {
            case DOCTOR -> {
                DoctorProfile dp = doctorProfileRepository
                    .findByUserId(user.getId()).orElse(null);
                yield userMapper.toResponseWithDoctorProfile(user, dp);
            }
            case PATIENT -> {
                PatientProfile pp = patientProfileRepository
                    .findByUserId(user.getId()).orElse(null);
                yield userMapper.toResponseWithPatientProfile(user, pp);
            }
            default -> userMapper.toResponse(user);
        };
    }

    private UserResponse updateDoctorProfile(User user, UpdateProfileRequest req) {
        DoctorProfile profile = doctorProfileRepository
            .findByUserId(user.getId())
            .orElseGet(() -> DoctorProfile.builder().user(user).build());

        Optional.ofNullable(req.getSpecialization()).ifPresent(profile::setSpecialization);
        Optional.ofNullable(req.getExperienceYears()).ifPresent(profile::setExperienceYears);
        Optional.ofNullable(req.getDepartment()).ifPresent(profile::setDepartment);
        Optional.ofNullable(req.getConsultationFee()).ifPresent(profile::setConsultationFee);
        Optional.ofNullable(req.getAvailableFrom()).ifPresent(profile::setAvailableFrom);
        Optional.ofNullable(req.getAvailableTo()).ifPresent(profile::setAvailableTo);

        doctorProfileRepository.save(profile);
        return userMapper.toResponseWithDoctorProfile(user, profile);
    }

    private UserResponse updatePatientProfile(User user, UpdateProfileRequest req) {
        PatientProfile profile = patientProfileRepository
            .findByUserId(user.getId())
            .orElseGet(() -> PatientProfile.builder().user(user).build());

        Optional.ofNullable(req.getBloodGroup()).ifPresent(profile::setBloodGroup);
        Optional.ofNullable(req.getEmergencyContact()).ifPresent(profile::setEmergencyContact);
        Optional.ofNullable(req.getAllergies()).ifPresent(profile::setAllergies);
        Optional.ofNullable(req.getChronicConditions()).ifPresent(profile::setChronicConditions);
        Optional.ofNullable(req.getInsuranceProvider()).ifPresent(profile::setInsuranceProvider);
        Optional.ofNullable(req.getInsuranceNumber()).ifPresent(profile::setInsuranceNumber);

        patientProfileRepository.save(profile);
        return userMapper.toResponseWithPatientProfile(user, profile);
    }

    private UserResponse createDoctorProfileFromAdmin(User user, AdminCreateUserRequest req) {
        DoctorProfile profile = DoctorProfile.builder()
            .user(user)
            .specialization(req.getSpecialization())
            .licenseNumber(req.getLicenseNumber())
            .experienceYears(req.getExperienceYears())
            .department(req.getDepartment())
            .consultationFee(req.getConsultationFee() != null
                ? req.getConsultationFee() : java.math.BigDecimal.ZERO)
            .availableFrom(req.getAvailableFrom())
            .availableTo(req.getAvailableTo())
            .build();
        doctorProfileRepository.save(profile);
        return userMapper.toResponseWithDoctorProfile(user, profile);
    }

    private UserResponse createPatientProfileFromAdmin(User user, AdminCreateUserRequest req) {
        PatientProfile profile = PatientProfile.builder()
            .user(user)
            .bloodGroup(req.getBloodGroup())
            .emergencyContact(req.getEmergencyContact())
            .allergies(req.getAllergies())
            .chronicConditions(req.getChronicConditions())
            .insuranceProvider(req.getInsuranceProvider())
            .insuranceNumber(req.getInsuranceNumber())
            .build();
        patientProfileRepository.save(profile);
        return userMapper.toResponseWithPatientProfile(user, profile);
    }
}
