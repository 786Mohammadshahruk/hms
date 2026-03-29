package com.hms.appointment.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

/**
 * Client for calling User Service via WebClient (load-balanced).
 * Falls back to empty Optional on any error so appointment operations don't fail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final WebClient.Builder webClientBuilder;

    public Optional<UserInfo> getUserById(Long userId) {
        try {
            UserInfo info = webClientBuilder.build()
                .get()
                .uri("lb://user-service/api/v1/users/{id}", userId)
                .retrieve()
                .bodyToMono(UserApiResponse.class)
                .map(UserApiResponse::getData)
                .block();
            return Optional.ofNullable(info);
        } catch (Exception e) {
            log.warn("Could not fetch user {} from user-service: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Inner classes ──────────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UserInfo {
        private Long   id;
        private String email;
        private String firstName;
        private String lastName;
        private String fullName;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    private static class UserApiResponse {
        private boolean  success;
        private String   message;
        private UserInfo data;
    }
}
