package com.hms.user.service;

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
import com.hms.user.mapper.UserMapper;
import com.hms.user.repository.DoctorProfileRepository;
import com.hms.user.repository.PatientProfileRepository;
import com.hms.user.repository.UserRepository;
import com.hms.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock UserRepository           userRepository;
    @Mock DoctorProfileRepository  doctorProfileRepository;
    @Mock PatientProfileRepository patientProfileRepository;
    @Mock PasswordEncoder          passwordEncoder;
    @Mock UserMapper               userMapper;
    @Mock UserEventPublisher       eventPublisher;

    @InjectMocks UserServiceImpl userService;

    private User patientUser;
    private User doctorUser;
    private UserResponse patientResponse;
    private UserResponse doctorResponse;

    @BeforeEach
    void setUp() {
        patientUser = User.builder()
            .id(1L).uuid(UUID.randomUUID())
            .firstName("Alice").lastName("Walker")
            .email("alice@example.com")
            .passwordHash("$2a$12$hash").role(Role.PATIENT).active(true)
            .build();

        doctorUser = User.builder()
            .id(2L).uuid(UUID.randomUUID())
            .firstName("Bob").lastName("Brown")
            .email("bob@example.com")
            .passwordHash("$2a$12$hash").role(Role.DOCTOR).active(true)
            .build();

        patientResponse = UserResponse.builder()
            .id(1L).email("alice@example.com").role(Role.PATIENT)
            .firstName("Alice").lastName("Walker").fullName("Alice Walker").build();

        doctorResponse = UserResponse.builder()
            .id(2L).email("bob@example.com").role(Role.DOCTOR)
            .firstName("Bob").lastName("Brown").fullName("Bob Brown")
            .specialization("Cardiology").build();
    }

    // ── Get profile ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("Should return patient with profile data")
        void getById_patient_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(patientUser));
            when(patientProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
            when(userMapper.toResponseWithPatientProfile(patientUser, null))
                .thenReturn(patientResponse);

            UserResponse result = userService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getRole()).isEqualTo(Role.PATIENT);
        }

        @Test
        @DisplayName("Should return doctor with profile data")
        void getById_doctor_success() {
            DoctorProfile profile = DoctorProfile.builder()
                .id(1L).user(doctorUser).specialization("Cardiology")
                .consultationFee(BigDecimal.valueOf(500)).build();

            when(userRepository.findById(2L)).thenReturn(Optional.of(doctorUser));
            when(doctorProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
            when(userMapper.toResponseWithDoctorProfile(doctorUser, profile))
                .thenReturn(doctorResponse);

            UserResponse result = userService.getById(2L);

            assertThat(result.getSpecialization()).isEqualTo("Cardiology");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException for non-existent user")
        void getById_notFound_throws() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User");
        }
    }

    // ── Update profile ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfileTests {

        @Test
        @DisplayName("Should update patient profile fields successfully")
        void updateProfile_patient_success() {
            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setFirstName("AliceUpdated");
            req.setPhone("9876543210");
            req.setAllergies("Peanuts");

            PatientProfile profile = PatientProfile.builder()
                .id(1L).user(patientUser).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(patientUser));
            when(userRepository.save(any())).thenReturn(patientUser);
            when(patientProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
            when(patientProfileRepository.save(any())).thenReturn(profile);
            when(userMapper.toResponseWithPatientProfile(any(), any()))
                .thenReturn(patientResponse);

            UserResponse result = userService.updateProfile(1L, req);

            assertThat(result).isNotNull();
            verify(patientProfileRepository).save(any());
            verify(eventPublisher).publishUserUpdated(any());
        }

        @Test
        @DisplayName("Should update doctor profile fields successfully")
        void updateProfile_doctor_success() {
            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setSpecialization("Neurology");
            req.setConsultationFee(BigDecimal.valueOf(750));

            DoctorProfile profile = DoctorProfile.builder()
                .id(1L).user(doctorUser).specialization("Cardiology").build();

            when(userRepository.findById(2L)).thenReturn(Optional.of(doctorUser));
            when(userRepository.save(any())).thenReturn(doctorUser);
            when(doctorProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
            when(doctorProfileRepository.save(any())).thenReturn(profile);
            when(userMapper.toResponseWithDoctorProfile(any(), any())).thenReturn(doctorResponse);

            userService.updateProfile(2L, req);

            verify(doctorProfileRepository).save(argThat(dp ->
                "Neurology".equals(dp.getSpecialization()) &&
                BigDecimal.valueOf(750).equals(dp.getConsultationFee())
            ));
        }
    }

    // ── Change password ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should change password when current password is correct")
        void changePassword_success() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("OldPass1@");
            req.setNewPassword("NewPass1@");
            req.setConfirmPassword("NewPass1@");

            when(userRepository.findById(1L)).thenReturn(Optional.of(patientUser));
            when(passwordEncoder.matches("OldPass1@", patientUser.getPasswordHash()))
                .thenReturn(true);
            when(passwordEncoder.encode("NewPass1@")).thenReturn("$2a$12$newHash");
            when(userRepository.save(any())).thenReturn(patientUser);

            assertThatNoException().isThrownBy(() -> userService.changePassword(1L, req));
            verify(userRepository).save(argThat(u ->
                "$2a$12$newHash".equals(u.getPasswordHash())));
        }

        @Test
        @DisplayName("Should throw BadRequestException when passwords do not match")
        void changePassword_mismatch_throws() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("OldPass1@");
            req.setNewPassword("NewPass1@");
            req.setConfirmPassword("DifferentPass1@");

            assertThatThrownBy(() -> userService.changePassword(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do not match");
        }

        @Test
        @DisplayName("Should throw BadRequestException when current password is wrong")
        void changePassword_wrongCurrent_throws() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("WrongPass1@");
            req.setNewPassword("NewPass1@");
            req.setConfirmPassword("NewPass1@");

            when(userRepository.findById(1L)).thenReturn(Optional.of(patientUser));
            when(passwordEncoder.matches("WrongPass1@", patientUser.getPasswordHash()))
                .thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");
        }
    }

    // ── Deactivate user ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deactivateUser()")
    class DeactivateTests {

        @Test
        @DisplayName("Should deactivate user successfully")
        void deactivate_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(patientUser));
            when(userRepository.save(any())).thenReturn(patientUser);

            userService.deactivateUser(1L, 99L);  // admin id = 99

            verify(userRepository).save(argThat(u -> !u.isActive()));
        }

        @Test
        @DisplayName("Should throw BadRequestException when user tries to deactivate themselves")
        void deactivate_self_throws() {
            assertThatThrownBy(() -> userService.deactivateUser(1L, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot deactivate your own account");
        }
    }

    // ── Admin use cases ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("adminCreateUser()")
    class AdminCreateUserTests {

        @Test
        @DisplayName("Should create a PATIENT user successfully")
        void adminCreateUser_patient_success() {
            AdminCreateUserRequest req = new AdminCreateUserRequest();
            req.setFirstName("New"); req.setLastName("Patient");
            req.setEmail("newpatient@example.com"); req.setPhone("5556667777");
            req.setPassword("Admin1@pass"); req.setRole(Role.PATIENT);

            when(userRepository.existsByEmail("newpatient@example.com")).thenReturn(false);
            when(userRepository.existsByPhone("5556667777")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
            when(userRepository.save(any())).thenReturn(patientUser);
            when(patientProfileRepository.save(any())).thenReturn(null);
            when(userMapper.toResponseWithPatientProfile(any(), any())).thenReturn(patientResponse);

            UserResponse result = userService.adminCreateUser(req, 99L);

            assertThat(result).isNotNull();
            assertThat(result.getRole()).isEqualTo(Role.PATIENT);
            verify(eventPublisher).publishUserRegistered(any());
        }

        @Test
        @DisplayName("Should create a DOCTOR user and save doctor profile")
        void adminCreateUser_doctor_success() {
            AdminCreateUserRequest req = new AdminCreateUserRequest();
            req.setFirstName("Dr"); req.setLastName("New");
            req.setEmail("drnew@example.com");
            req.setPassword("Admin1@pass"); req.setRole(Role.DOCTOR);
            req.setSpecialization("Cardiology"); req.setLicenseNumber("LIC-NEW");

            DoctorProfile profile = DoctorProfile.builder()
                .id(1L).user(doctorUser).specialization("Cardiology").build();

            when(userRepository.existsByEmail("drnew@example.com")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
            when(userRepository.save(any())).thenReturn(doctorUser);
            when(doctorProfileRepository.save(any())).thenReturn(profile);
            when(userMapper.toResponseWithDoctorProfile(any(), any())).thenReturn(doctorResponse);

            UserResponse result = userService.adminCreateUser(req, 99L);

            assertThat(result.getRole()).isEqualTo(Role.DOCTOR);
            verify(doctorProfileRepository).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException for duplicate email")
        void adminCreateUser_duplicateEmail_throws() {
            AdminCreateUserRequest req = new AdminCreateUserRequest();
            req.setEmail("alice@example.com"); req.setRole(Role.PATIENT);
            req.setFirstName("X"); req.setLastName("Y"); req.setPassword("Admin1@pass");

            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.adminCreateUser(req, 99L))
                .isInstanceOf(com.hms.user.exception.BadRequestException.class)
                .hasMessageContaining("Email already registered");
        }

        @Test
        @DisplayName("Should throw BadRequestException when DOCTOR missing specialization")
        void adminCreateUser_doctorMissingSpecialization_throws() {
            AdminCreateUserRequest req = new AdminCreateUserRequest();
            req.setEmail("drnew2@example.com"); req.setRole(Role.DOCTOR);
            req.setFirstName("Dr"); req.setLastName("X"); req.setPassword("Admin1@pass");
            req.setLicenseNumber("LIC999");
            // specialization intentionally absent

            when(userRepository.existsByEmail(anyString())).thenReturn(false);

            assertThatThrownBy(() -> userService.adminCreateUser(req, 99L))
                .isInstanceOf(com.hms.user.exception.BadRequestException.class)
                .hasMessageContaining("Specialization is required");
        }
    }

    @Nested
    @DisplayName("changeUserRole()")
    class ChangeUserRoleTests {

        @Test
        @DisplayName("Should change role successfully")
        void changeUserRole_success() {
            ChangeRoleRequest req = new ChangeRoleRequest(Role.DOCTOR, "Promoted to doctor");
            when(userRepository.findById(1L)).thenReturn(Optional.of(patientUser));
            when(userRepository.save(any())).thenReturn(patientUser);
            when(doctorProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
            when(userMapper.toResponseWithDoctorProfile(any(), any())).thenReturn(doctorResponse);

            UserResponse result = userService.changeUserRole(1L, req, 99L);

            assertThat(result).isNotNull();
            verify(userRepository).save(argThat(u -> u.getRole() == Role.DOCTOR));
        }

        @Test
        @DisplayName("Should throw BadRequestException when role is same")
        void changeUserRole_sameRole_throws() {
            ChangeRoleRequest req = new ChangeRoleRequest(Role.PATIENT, "No change");
            when(userRepository.findById(1L)).thenReturn(Optional.of(patientUser));

            assertThatThrownBy(() -> userService.changeUserRole(1L, req, 99L))
                .isInstanceOf(com.hms.user.exception.BadRequestException.class)
                .hasMessageContaining("already has role");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when target user not found")
        void changeUserRole_userNotFound_throws() {
            ChangeRoleRequest req = new ChangeRoleRequest(Role.ADMIN, "reason");
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changeUserRole(999L, req, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("adminResetPassword()")
    class AdminResetPasswordTests {

        @Test
        @DisplayName("Should reset password and save encoded hash")
        void adminResetPassword_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(patientUser));
            when(passwordEncoder.encode("NewPass1@")).thenReturn("$2a$12$newHash");
            when(userRepository.save(any())).thenReturn(patientUser);

            assertThatNoException().isThrownBy(
                () -> userService.adminResetPassword(1L, "NewPass1@", 99L));

            verify(userRepository).save(argThat(u -> "$2a$12$newHash".equals(u.getPasswordHash())));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when target user not found")
        void adminResetPassword_userNotFound_throws() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.adminResetPassword(999L, "NewPass1@", 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Get all users paginated ────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsersTests {

        @Test
        @DisplayName("Should return paginated users when no filters applied")
        void getAllUsers_noFilter_success() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> page = new PageImpl<>(List.of(patientUser, doctorUser));

            when(userRepository.findAllByActiveTrue(pageable)).thenReturn(page);
            when(patientProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
            when(doctorProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());
            when(userMapper.toResponseWithPatientProfile(any(), any())).thenReturn(patientResponse);
            when(userMapper.toResponseWithDoctorProfile(any(), any())).thenReturn(doctorResponse);

            PagedResponse<UserResponse> result =
                userService.getAllUsers(null, null, null, pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getPageNumber()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should filter by role when role is provided")
        void getAllUsers_withRoleFilter_success() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> page = new PageImpl<>(List.of(doctorUser));

            when(userRepository.findAllByRole(Role.DOCTOR, pageable)).thenReturn(page);
            when(doctorProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());
            when(userMapper.toResponseWithDoctorProfile(any(), any())).thenReturn(doctorResponse);

            PagedResponse<UserResponse> result =
                userService.getAllUsers(null, Role.DOCTOR, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getRole()).isEqualTo(Role.DOCTOR);
        }
    }
}
