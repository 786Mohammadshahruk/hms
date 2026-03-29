package com.hms.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Standardized API response wrapper used across ALL endpoints.
 * Every controller method returns ApiResponse<T>.
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "User registered successfully",
 *   "data": { ... },
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 * </pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String  message;
    private final T       data;
    private final Object  errors;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    // ── Static factory helpers ─────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .build();
    }

    public static <T> ApiResponse<T> error(String message, Object errors) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .errors(errors)
            .build();
    }
}
