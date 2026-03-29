package com.hms.user.service.impl;

import com.hms.user.dto.request.LoginRequest;
import com.hms.user.dto.request.RefreshTokenRequest;
import com.hms.user.dto.request.RegisterRequest;
import com.hms.user.dto.response.AuthResponse;
import com.hms.user.dto.response.UserResponse;
import com.hms.user.entity.DoctorProfile;
import com.hms.user.entity.PatientProfile;
import com.hms.user.entity.RefreshToken;
import com.hms.user.entity.User;
import com.hms.user.enums.Role;
import com.hms.user.exception.BadRequestException;
import com.hms.user.exception.DuplicateResourceException;
import com.hms.user.exception.InvalidCredentialsException;
import com.hms.user.exception.InvalidTokenException;
import com.hms.user.exception.ResourceNotFoundException;
import com.hms.user.kafka.UserEventPublisher;
import com.hms.user.kafka.UserEvents;
import com.hms.user.mapper.UserMapper;
import com.hms.user.repository.DoctorProfileRepository;
import com.hms.user.repository.PatientProfileRepository;
import com.hms.user.repository.RefreshTokenRepository;
import com.hms.user.repository.UserRepository;
import com.hms.user.security.JwtTokenProvider;
import com.hms.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository           userRepository;
    private final DoctorProfileRepository  doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final RefreshTokenRepository   refreshTokenRepository;
    private final PasswordEncoder          passwordEncoder;
    private final JwtTokenProvider         jwtTokenProvider;
    private final AuthenticationManager    authenticationManager;
    private final UserMapper               userMapper;
    private final UserEventPublisher       eventPublisher;

    // ── Register ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // Public registration is restricted to PATIENT role only
        if (request.getRole() != null && request.getRole() != Role.PATIENT) {
            throw new BadRequestException(
                "Public registration is only allowed for the PATIENT role. " +
                "Contact an admin to create accounts with other roles.");
        }

        // Duplicate checks
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                "Email already registered: " + request.getEmail());
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException(
                "Phone already registered: " + request.getPhone());
        }

        // Build and persist User
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
        log.info("User saved with id={}", user.getId());

        // Create role-specific profile
        UserResponse response = switch (request.getRole()) {
            case DOCTOR  -> createDoctorProfile(user, request);
            case PATIENT -> createPatientProfile(user, request);
            default      -> userMapper.toResponse(user);
        };

        // Publish async Kafka event
        final Long   userId   = user.getId();
        final String fullName = user.getFullName();
        final Role   role     = user.getRole();
        eventPublisher.publishUserRegistered(UserEvents.UserRegisteredEvent.builder()
            .userId(userId)
            .email(user.getEmail())
            .fullName(fullName)
            .role(role)
            .occurredAt(Instant.now())
            .build());

        return response;
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail().toLowerCase(),
                    request.getPassword()
                )
            );
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!user.isActive()) {
            throw new InvalidCredentialsException("Account is deactivated. Contact admin.");
        }

        // Revoke previous refresh tokens (single session per user)
        refreshTokenRepository.deleteAllByUserId(user.getId());

        String accessToken  = jwtTokenProvider.generateAccessToken(
            user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(
            user.getId(), user.getEmail());

        saveRefreshToken(user, refreshToken);

        log.info("Login successful for userId={}", user.getId());

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getRefreshExpirationMs() / 1000)
            .userId(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .role(user.getRole())
            .build();
    }

    // ── Refresh Token ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository
            .findByToken(request.getRefreshToken())
            .orElseThrow(() -> new InvalidTokenException("Refresh token not found or already used"));

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new InvalidTokenException("Refresh token has expired. Please login again.");
        }

        User user = storedToken.getUser();

        // Rotate: delete old, issue new pair
        refreshTokenRepository.delete(storedToken);

        String newAccessToken  = jwtTokenProvider.generateAccessToken(
            user.getId(), user.getEmail(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(
            user.getId(), user.getEmail());

        saveRefreshToken(user, newRefreshToken);

        return AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getRefreshExpirationMs() / 1000)
            .userId(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .role(user.getRole())
            .build();
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
        log.info("Logged out userId={}", userId);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void validateDoctorFields(RegisterRequest request) {
        if (request.getSpecialization() == null || request.getSpecialization().isBlank()) {
            throw new BadRequestException("Specialization is required for DOCTOR role");
        }
        if (request.getLicenseNumber() == null || request.getLicenseNumber().isBlank()) {
            throw new BadRequestException("License number is required for DOCTOR role");
        }
        if (doctorProfileRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw new DuplicateResourceException(
                "License number already registered: " + request.getLicenseNumber());
        }
    }

    private UserResponse createDoctorProfile(User user, RegisterRequest req) {
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

    private UserResponse createPatientProfile(User user, RegisterRequest req) {
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

    private void saveRefreshToken(User user, String rawToken) {
        Instant expiresAt = Instant.now().plusMillis(
            jwtTokenProvider.getRefreshExpirationMs());
        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .token(rawToken)
            .expiresAt(expiresAt)
            .build());
    }
}
