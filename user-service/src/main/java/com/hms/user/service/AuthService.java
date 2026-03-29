package com.hms.user.service;

import com.hms.user.dto.request.LoginRequest;
import com.hms.user.dto.request.RefreshTokenRequest;
import com.hms.user.dto.request.RegisterRequest;
import com.hms.user.dto.response.AuthResponse;
import com.hms.user.dto.response.UserResponse;

public interface AuthService {

    /**
     * Registers a new user, creates role-specific profile, publishes event.
     * @return the created user's public representation
     */
    UserResponse register(RegisterRequest request);

    /**
     * Authenticates credentials, returns access + refresh tokens.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Rotates a refresh token — invalidates old, issues new pair.
     */
    AuthResponse refreshToken(RefreshTokenRequest request);

    /**
     * Invalidates all refresh tokens for the given user (logout).
     */
    void logout(Long userId);
}
