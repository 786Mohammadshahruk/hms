package com.hms.user.controller;

import com.hms.user.dto.request.LoginRequest;
import com.hms.user.dto.request.RefreshTokenRequest;
import com.hms.user.dto.request.RegisterRequest;
import com.hms.user.dto.response.ApiResponse;
import com.hms.user.dto.response.AuthResponse;
import com.hms.user.dto.response.UserResponse;
import com.hms.user.security.UserDetailsImpl;
import com.hms.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller — public and authenticated endpoints for
 * user registration, login, token refresh, and logout.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user (patient, doctor, or cashier)")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        UserResponse user = authService.register(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("User registered successfully", user));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password, receive JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", auth));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and get a new access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthResponse auth = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", auth));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout — invalidates all refresh tokens for the current user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetailsImpl currentUser) {

        authService.logout(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }
}
