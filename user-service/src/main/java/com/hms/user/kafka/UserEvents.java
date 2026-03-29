package com.hms.user.kafka;

import com.hms.user.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Events published to Kafka by the User Service.
 * Consumed by Notification Service (and others) for async processing.
 */
public class UserEvents {

    // ── User registered ────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserRegisteredEvent {
        private Long    userId;
        private String  email;
        private String  fullName;
        private Role    role;
        private Instant occurredAt;
    }

    // ── Password reset requested ───────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PasswordResetRequestedEvent {
        private Long    userId;
        private String  email;
        private String  fullName;
        private String  resetToken;       // short-lived OTP or link token
        private Instant occurredAt;
    }

    // ── Profile updated ────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserUpdatedEvent {
        private Long    userId;
        private String  email;
        private String  fullName;
        private Instant occurredAt;
    }
}
