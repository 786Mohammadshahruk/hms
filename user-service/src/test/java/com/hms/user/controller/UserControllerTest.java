package com.hms.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hms.user.dto.request.AdminCreateUserRequest;
import com.hms.user.dto.request.AdminResetPasswordRequest;
import com.hms.user.dto.request.ChangePasswordRequest;
import com.hms.user.dto.request.ChangeRoleRequest;
import com.hms.user.dto.request.UpdateProfileRequest;
import com.hms.user.dto.response.PagedResponse;
import com.hms.user.dto.response.UserResponse;
import com.hms.user.entity.User;
import com.hms.user.enums.Role;
import com.hms.user.exception.BadRequestException;
import com.hms.user.exception.ResourceNotFoundException;
import com.hms.user.security.UserDetailsImpl;
import com.hms.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController Tests")
class UserControllerTest {

    @Mock UserService userService;
    @InjectMocks UserController userController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    private UserDetailsImpl patientPrincipal;
    private UserDetailsImpl adminPrincipal;
    private UserResponse patientResponse;
    private UserResponse adminResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
            .standaloneSetup(userController)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        User patientUser = User.builder().id(1L).uuid(UUID.randomUUID())
            .firstName("Alice").lastName("Walker").email("alice@example.com")
            .passwordHash("hash").role(Role.PATIENT).active(true).build();
        patientPrincipal = new UserDetailsImpl(patientUser);

        User adminUser = User.builder().id(99L).uuid(UUID.randomUUID())
            .firstName("Admin").lastName("User").email("admin@example.com")
            .passwordHash("hash").role(Role.ADMIN).active(true).build();
        adminPrincipal = new UserDetailsImpl(adminUser);

        patientResponse = UserResponse.builder()
            .id(1L).email("alice@example.com").role(Role.PATIENT)
            .firstName("Alice").lastName("Walker").fullName("Alice Walker").build();

        adminResponse = UserResponse.builder()
            .id(99L).email("admin@example.com").role(Role.ADMIN)
            .firstName("Admin").lastName("User").fullName("Admin User").build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UserDetailsImpl principal) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    // ── GET /me ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetMyProfileTests {

        @Test
        @DisplayName("Should return 200 with authenticated user's own profile")
        void getMyProfile_success() throws Exception {
            authenticateAs(patientPrincipal);
            when(userService.getMyProfile(1L)).thenReturn(patientResponse);

            mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.role").value("PATIENT"));

            verify(userService).getMyProfile(1L);
        }
    }

    // ── PUT /me ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/users/me")
    class UpdateProfileTests {

        @Test
        @DisplayName("Should return 200 with updated profile")
        void updateProfile_success() throws Exception {
            authenticateAs(patientPrincipal);

            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setFirstName("AliceUpdated");

            UserResponse updated = UserResponse.builder()
                .id(1L).email("alice@example.com").firstName("AliceUpdated")
                .lastName("Walker").role(Role.PATIENT).build();

            when(userService.updateProfile(eq(1L), any())).thenReturn(updated);

            mockMvc.perform(put("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("AliceUpdated"));

            verify(userService).updateProfile(eq(1L), any(UpdateProfileRequest.class));
        }
    }

    // ── PATCH /me/password ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/users/me/password")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should return 200 when password changed successfully")
        void changePassword_success() throws Exception {
            authenticateAs(patientPrincipal);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("OldPass1@");
            req.setNewPassword("NewPass1@");
            req.setConfirmPassword("NewPass1@");

            doNothing().when(userService).changePassword(eq(1L), any());

            mockMvc.perform(patch("/api/v1/users/me/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
        }

        @Test
        @DisplayName("Should return 400 when passwords do not match")
        void changePassword_mismatch_returns400() throws Exception {
            authenticateAs(patientPrincipal);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("OldPass1@");
            req.setNewPassword("NewPass1@");
            req.setConfirmPassword("Different1@");

            doThrow(new BadRequestException("New password and confirm password do not match"))
                .when(userService).changePassword(anyLong(), any());

            mockMvc.perform(patch("/api/v1/users/me/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ── GET /{id} ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/users/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("Should return 200 when user exists")
        void getUserById_success() throws Exception {
            when(userService.getById(1L)).thenReturn(patientResponse);

            mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
        }

        @Test
        @DisplayName("Should return 404 when user does not exist")
        void getUserById_notFound_returns404() throws Exception {
            when(userService.getById(999L))
                .thenThrow(new ResourceNotFoundException("User", "id", 999L));

            mockMvc.perform(get("/api/v1/users/999"))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /doctors ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/users/doctors")
    class GetDoctorsTests {

        @Test
        @DisplayName("Should return 200 with paginated list of doctors")
        void getDoctors_success() throws Exception {
            UserResponse doctorResponse = UserResponse.builder()
                .id(2L).email("dr@example.com").role(Role.DOCTOR)
                .firstName("Dr").lastName("Smith").specialization("Cardiology").build();

            PagedResponse<UserResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(doctorResponse)));

            when(userService.getDoctors(any(), any(), any())).thenReturn(paged);

            mockMvc.perform(get("/api/v1/users/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].role").value("DOCTOR"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    // ── GET (list all users) ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/users")
    class GetAllUsersTests {

        @Test
        @DisplayName("Should return 200 with paginated users list")
        void getAllUsers_success() throws Exception {
            PagedResponse<UserResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(patientResponse)));

            when(userService.getAllUsers(any(), any(), any(), any())).thenReturn(paged);

            mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    // ── PATCH /admin/{id}/deactivate ───────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/users/admin/{id}/deactivate")
    class DeactivateTests {

        @Test
        @DisplayName("Should return 200 when user is deactivated")
        void deactivate_success() throws Exception {
            authenticateAs(adminPrincipal);
            doNothing().when(userService).deactivateUser(1L, 99L);

            mockMvc.perform(patch("/api/v1/users/admin/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User deactivated successfully"));
        }
    }

    // ── PATCH /admin/{id}/activate ─────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/users/admin/{id}/activate")
    class ActivateTests {

        @Test
        @DisplayName("Should return 200 when user is activated")
        void activate_success() throws Exception {
            doNothing().when(userService).activateUser(1L);

            mockMvc.perform(patch("/api/v1/users/admin/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User activated successfully"));
        }
    }

    // ── POST /admin/create ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/users/admin/create")
    class AdminCreateUserTests {

        @Test
        @DisplayName("Should return 201 when admin creates a new user")
        void adminCreate_success_returns201() throws Exception {
            authenticateAs(adminPrincipal);

            AdminCreateUserRequest req = new AdminCreateUserRequest();
            req.setFirstName("New"); req.setLastName("Doctor");
            req.setEmail("newdoc@example.com"); req.setPassword("Admin1@pass");
            req.setRole(Role.DOCTOR);
            req.setSpecialization("Radiology"); req.setLicenseNumber("LIC-999");

            UserResponse created = UserResponse.builder()
                .id(5L).email("newdoc@example.com").role(Role.DOCTOR)
                .firstName("New").lastName("Doctor").build();

            when(userService.adminCreateUser(any(), eq(99L))).thenReturn(created);

            mockMvc.perform(post("/api/v1/users/admin/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("DOCTOR"))
                .andExpect(jsonPath("$.data.email").value("newdoc@example.com"));
        }
    }

    // ── PATCH /admin/{id}/role ─────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/users/admin/{id}/role")
    class ChangeRoleTests {

        @Test
        @DisplayName("Should return 200 when role is changed successfully")
        void changeRole_success() throws Exception {
            authenticateAs(adminPrincipal);

            ChangeRoleRequest req = new ChangeRoleRequest(Role.DOCTOR, "Promoted");

            UserResponse updated = UserResponse.builder()
                .id(1L).email("alice@example.com").role(Role.DOCTOR).build();

            when(userService.changeUserRole(eq(1L), any(), eq(99L))).thenReturn(updated);

            mockMvc.perform(patch("/api/v1/users/admin/1/role")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("DOCTOR"))
                .andExpect(jsonPath("$.message").value("User role updated successfully"));
        }
    }

    // ── POST /admin/{id}/reset-password ───────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/users/admin/{id}/reset-password")
    class AdminResetPasswordTests {

        @Test
        @DisplayName("Should return 200 when password is reset successfully")
        void resetPassword_success() throws Exception {
            authenticateAs(adminPrincipal);

            AdminResetPasswordRequest req = new AdminResetPasswordRequest("NewAdmin1@");

            doNothing().when(userService).adminResetPassword(eq(1L), eq("NewAdmin1@"), eq(99L));

            mockMvc.perform(post("/api/v1/users/admin/1/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully"));
        }

        @Test
        @DisplayName("Should return 404 when target user not found")
        void resetPassword_userNotFound_returns404() throws Exception {
            authenticateAs(adminPrincipal);

            AdminResetPasswordRequest req = new AdminResetPasswordRequest("NewAdmin1@");

            doThrow(new ResourceNotFoundException("User", "id", 999L))
                .when(userService).adminResetPassword(eq(999L), any(), anyLong());

            mockMvc.perform(post("/api/v1/users/admin/999/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
        }
    }
}
