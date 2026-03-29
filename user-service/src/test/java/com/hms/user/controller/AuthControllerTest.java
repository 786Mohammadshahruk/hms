package com.hms.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hms.user.dto.request.LoginRequest;
import com.hms.user.dto.request.RefreshTokenRequest;
import com.hms.user.dto.request.RegisterRequest;
import com.hms.user.dto.response.AuthResponse;
import com.hms.user.dto.response.UserResponse;
import com.hms.user.entity.User;
import com.hms.user.enums.Role;
import com.hms.user.exception.BadRequestException;
import com.hms.user.exception.DuplicateResourceException;
import com.hms.user.exception.InvalidCredentialsException;
import com.hms.user.exception.InvalidTokenException;
import com.hms.user.security.UserDetailsImpl;
import com.hms.user.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Mock AuthService authService;
    @InjectMocks AuthController authController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
            .standaloneSetup(authController)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── POST /register ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Should return 201 and user response on successful registration")
        void register_success_returns201() throws Exception {
            RegisterRequest req = RegisterRequest.builder()
                .firstName("John").lastName("Doe")
                .email("john@example.com").password("Secret1@")
                .role(Role.PATIENT).build();

            UserResponse mockUser = UserResponse.builder()
                .id(1L).email("john@example.com").role(Role.PATIENT)
                .firstName("John").lastName("Doe").build();

            when(authService.register(any())).thenReturn(mockUser);

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.email").value("john@example.com"))
                .andExpect(jsonPath("$.data.role").value("PATIENT"));

            verify(authService).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("Should return 409 when email is already registered")
        void register_duplicateEmail_returns409() throws Exception {
            RegisterRequest req = RegisterRequest.builder()
                .firstName("John").lastName("Doe")
                .email("taken@example.com").password("Secret1@")
                .role(Role.PATIENT).build();

            when(authService.register(any()))
                .thenThrow(new DuplicateResourceException("Email already registered: taken@example.com"));

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
        }
    }

    // ── POST /login ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Should return 200 with access and refresh tokens on success")
        void login_success_returns200() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("john@example.com");
            req.setPassword("Secret1@");

            AuthResponse auth = AuthResponse.builder()
                .accessToken("eyJ.access")
                .refreshToken("eyJ.refresh")
                .tokenType("Bearer")
                .userId(1L)
                .email("john@example.com")
                .role(Role.PATIENT)
                .build();

            when(authService.login(any())).thenReturn(auth);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("eyJ.access"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.role").value("PATIENT"));

            verify(authService).login(any(LoginRequest.class));
        }

        @Test
        @DisplayName("Should return 401 on invalid credentials")
        void login_invalidCredentials_returns401() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("john@example.com");
            req.setPassword("WrongPass");

            when(authService.login(any()))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /refresh ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshTests {

        @Test
        @DisplayName("Should return 200 with new tokens on valid refresh token")
        void refresh_success_returns200() throws Exception {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("valid-refresh-token");

            AuthResponse auth = AuthResponse.builder()
                .accessToken("eyJ.new-access")
                .refreshToken("eyJ.new-refresh")
                .tokenType("Bearer")
                .userId(1L)
                .email("john@example.com")
                .role(Role.PATIENT)
                .build();

            when(authService.refreshToken(any())).thenReturn(auth);

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("eyJ.new-access"));
        }

        @Test
        @DisplayName("Should return 401 on expired or invalid refresh token")
        void refresh_invalidToken_returns401() throws Exception {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("expired-token");

            when(authService.refreshToken(any()))
                .thenThrow(new InvalidTokenException("Refresh token has expired"));

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /logout ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("Should return 200 and call logout with the authenticated user's ID")
        void logout_success_returns200() throws Exception {
            User user = User.builder()
                .id(1L).uuid(UUID.randomUUID()).email("john@example.com")
                .passwordHash("hash").role(Role.PATIENT).active(true).build();
            UserDetailsImpl principal = new UserDetailsImpl(user);

            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

            doNothing().when(authService).logout(1L);

            mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

            verify(authService).logout(1L);
        }
    }
}
