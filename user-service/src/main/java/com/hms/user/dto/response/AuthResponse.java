package com.hms.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.user.enums.Role;
import lombok.Builder;
import lombok.Getter;

/**
 * Returned on successful login or token refresh.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long   expiresIn;     // seconds
    private final Long   userId;
    private final String email;
    private final String fullName;
    private final Role   role;
}
