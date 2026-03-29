package com.hms.user.service;

import com.hms.user.dto.request.LoginRequest;
import com.hms.user.dto.request.RegisterRequest;
import com.hms.user.dto.response.AuthResponse;
import com.hms.user.dto.response.UserResponse;
import com.hms.user.entity.DoctorProfile;
import com.hms.user.entity.RefreshToken;
import com.hms.user.entity.User;
import com.hms.user.enums.Role;
import com.hms.user.exception.DuplicateResourceException;
import com.hms.user.exception.InvalidCredentialsException;
import com.hms.user.exception.InvalidTokenException;
import com.hms.user.kafka.UserEventPublisher;
import com.hms.user.mapper.UserMapper;
import com.hms.user.repository.DoctorProfileRepository;
import com.hms.user.repository.PatientProfileRepository;
import com.hms.user.repository.RefreshTokenRepository;
import com.hms.user.repository.UserRepository;
import com.hms.user.security.JwtTokenProvider;
import com.hms.user.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock UserRepository           userRepository;
    @Mock DoctorProfileRepository  doctorProfileRepository;
    @Mock PatientProfileRepository patientProfileRepository;
    @Mock RefreshTokenRepository   refreshTokenRepository;
    @Mock PasswordEncoder          passwordEncoder;
    @Mock JwtTokenProvider         jwtTokenProvider;
    @Mock AuthenticationManager    authenticationManager;
    @Mock UserMapper               userMapper;
    @Mock UserEventPublisher       eventPublisher;

    @InjectMocks AuthServiceImpl authService;

    private User testPatient;
    private User testDoctor;

    @BeforeEach
    void setUp() {
        testPatient = User.builder()
            .id(1L).uuid(UUID.randomUUID())
            .firstName("John").lastName("Doe")
            .email("john@example.com").phone("1234567890")
            .passwordHash("$2a$12$hashedpassword")
            .role(Role.PATIENT).active(true)
            .build();

        testDoctor = User.builder()
            .id(2L).uuid(UUID.randomUUID())
            .firstName("Dr").lastName("Smith")
            .email("drsmith@example.com")
            .passwordHash("$2a$12$hashedpassword")
            .role(Role.DOCTOR).active(true)
            .build();
    }

    // ── Register Tests ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("Should register a patient successfully")
        void register_patient_success() {
            RegisterRequest req = RegisterRequest.builder()
                .firstName("John").lastName("Doe")
                .email("john@example.com").phone("1234567890")
                .password("Password1@").role(Role.PATIENT)
                .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByPhone(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenReturn(testPatient);
            when(patientProfileRepository.save(any())).thenReturn(null);

            UserResponse mockResponse = UserResponse.builder()
                .id(1L).email("john@example.com").role(Role.PATIENT).build();
            when(userMapper.toResponseWithPatientProfile(any(), any())).thenReturn(mockResponse);

            UserResponse result = authService.register(req);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john@example.com");
            assertThat(result.getRole()).isEqualTo(Role.PATIENT);
            verify(userRepository).save(any(User.class));
            verify(patientProfileRepository).save(any());
            verify(eventPublisher).publishUserRegistered(any());
        }

        @Test
        @DisplayName("Should register a doctor with profile successfully")
        void register_doctor_success() {
            RegisterRequest req = RegisterRequest.builder()
                .firstName("Dr").lastName("Smith")
                .email("drsmith@example.com")
                .password("Password1@").role(Role.DOCTOR)
                .specialization("Cardiology").licenseNumber("LIC123")
                .consultationFee(BigDecimal.valueOf(500))
                .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(doctorProfileRepository.existsByLicenseNumber("LIC123")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenReturn(testDoctor);
            when(doctorProfileRepository.save(any())).thenReturn(new DoctorProfile());

            UserResponse mockResponse = UserResponse.builder()
                .id(2L).email("drsmith@example.com").role(Role.DOCTOR)
                .specialization("Cardiology").build();
            when(userMapper.toResponseWithDoctorProfile(any(), any())).thenReturn(mockResponse);

            UserResponse result = authService.register(req);

            assertThat(result.getRole()).isEqualTo(Role.DOCTOR);
            assertThat(result.getSpecialization()).isEqualTo("Cardiology");
            verify(doctorProfileRepository).save(any());
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when email already exists")
        void register_duplicateEmail_throws() {
            RegisterRequest req = RegisterRequest.builder()
                .email("john@example.com").role(Role.PATIENT)
                .firstName("John").lastName("Doe")
                .password("Password1@").build();

            when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email already registered");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when doctor missing specialization")
        void register_doctor_missingSpecialization_throws() {
            RegisterRequest req = RegisterRequest.builder()
                .email("dr@example.com").role(Role.DOCTOR)
                .firstName("Dr").lastName("Who")
                .password("Password1@")
                .licenseNumber("LIC999")
                // specialization intentionally missing
                .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(com.hms.user.exception.BadRequestException.class)
                .hasMessageContaining("Specialization is required");
        }

        @Test
        @DisplayName("Should throw BadRequestException when non-PATIENT role used in public register")
        void register_nonPatientRole_throws() {
            RegisterRequest req = RegisterRequest.builder()
                .email("admin@example.com").role(Role.ADMIN)
                .firstName("Super").lastName("Admin")
                .password("Password1@").build();

            assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(com.hms.user.exception.BadRequestException.class)
                .hasMessageContaining("PATIENT role");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when CASHIER role used in public register")
        void register_cashierRole_throws() {
            RegisterRequest req = RegisterRequest.builder()
                .email("cashier@example.com").role(Role.CASHIER)
                .firstName("Cash").lastName("Ier")
                .password("Password1@").build();

            assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(com.hms.user.exception.BadRequestException.class)
                .hasMessageContaining("PATIENT role");
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when license number already used")
        void register_doctor_duplicateLicense_throws() {
            RegisterRequest req = RegisterRequest.builder()
                .email("dr2@example.com").role(Role.DOCTOR)
                .firstName("Dr").lastName("Two")
                .password("Password1@")
                .specialization("Neurology").licenseNumber("DUPLICATE")
                .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(doctorProfileRepository.existsByLicenseNumber("DUPLICATE")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("License number already registered");
        }
    }

    // ── Login Tests ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("Should return tokens on successful login")
        void login_success() {
            LoginRequest req = new LoginRequest();
            req.setEmail("john@example.com");
            req.setPassword("Password1@");

            when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("john@example.com", null));
            when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(testPatient));
            when(jwtTokenProvider.generateAccessToken(1L, "john@example.com", Role.PATIENT))
                .thenReturn("access-token-123");
            when(jwtTokenProvider.generateRefreshToken(1L, "john@example.com"))
                .thenReturn("refresh-token-abc");
            when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
            when(refreshTokenRepository.save(any())).thenReturn(new RefreshToken());

            AuthResponse result = authService.login(req);

            assertThat(result.getAccessToken()).isEqualTo("access-token-123");
            assertThat(result.getRefreshToken()).isEqualTo("refresh-token-abc");
            assertThat(result.getTokenType()).isEqualTo("Bearer");
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getRole()).isEqualTo(Role.PATIENT);
            verify(refreshTokenRepository).deleteAllByUserId(1L);
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException on wrong password")
        void login_wrongPassword_throws() {
            LoginRequest req = new LoginRequest();
            req.setEmail("john@example.com");
            req.setPassword("WrongPassword1@");

            when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException when account is deactivated")
        void login_deactivatedAccount_throws() {
            testPatient.setActive(false);
            LoginRequest req = new LoginRequest();
            req.setEmail("john@example.com");
            req.setPassword("Password1@");

            when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("john@example.com", null));
            when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(testPatient));

            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("deactivated");
        }
    }

    // ── Refresh Token Tests ────────────────────────────────────────────────────

    @Nested
    @DisplayName("refreshToken()")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should return new token pair on valid refresh token")
        void refreshToken_success() {
            com.hms.user.dto.request.RefreshTokenRequest req =
                new com.hms.user.dto.request.RefreshTokenRequest();
            req.setRefreshToken("valid-refresh-token");

            RefreshToken storedToken = RefreshToken.builder()
                .id(1L).user(testPatient).token("valid-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

            when(refreshTokenRepository.findByToken("valid-refresh-token"))
                .thenReturn(Optional.of(storedToken));
            when(jwtTokenProvider.generateAccessToken(any(), any(), any()))
                .thenReturn("new-access-token");
            when(jwtTokenProvider.generateRefreshToken(any(), any()))
                .thenReturn("new-refresh-token");
            when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
            when(refreshTokenRepository.save(any())).thenReturn(new RefreshToken());

            AuthResponse result = authService.refreshToken(req);

            assertThat(result.getAccessToken()).isEqualTo("new-access-token");
            assertThat(result.getRefreshToken()).isEqualTo("new-refresh-token");
            verify(refreshTokenRepository).delete(storedToken);
        }

        @Test
        @DisplayName("Should throw InvalidTokenException when refresh token not found")
        void refreshToken_notFound_throws() {
            com.hms.user.dto.request.RefreshTokenRequest req =
                new com.hms.user.dto.request.RefreshTokenRequest();
            req.setRefreshToken("ghost-token");

            when(refreshTokenRepository.findByToken("ghost-token"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(req))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Refresh token not found");
        }

        @Test
        @DisplayName("Should throw InvalidTokenException when refresh token is expired")
        void refreshToken_expired_throws() {
            com.hms.user.dto.request.RefreshTokenRequest req =
                new com.hms.user.dto.request.RefreshTokenRequest();
            req.setRefreshToken("expired-token");

            RefreshToken expiredToken = RefreshToken.builder()
                .id(2L).user(testPatient).token("expired-token")
                .expiresAt(Instant.now().minusSeconds(3600))  // expired 1 hour ago
                .build();

            when(refreshTokenRepository.findByToken("expired-token"))
                .thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> authService.refreshToken(req))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");

            verify(refreshTokenRepository).delete(expiredToken);
        }
    }

    // ── Logout Tests ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("Should delete all refresh tokens for user on logout")
        void logout_deletesRefreshTokens() {
            authService.logout(1L);
            verify(refreshTokenRepository).deleteAllByUserId(1L);
        }
    }
}
