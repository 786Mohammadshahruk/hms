package com.hms.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hms.notification.dto.response.NotificationResponse;
import com.hms.notification.dto.response.PagedResponse;
import com.hms.notification.enums.NotificationStatus;
import com.hms.notification.enums.NotificationType;
import com.hms.notification.security.UserPrincipal;
import com.hms.notification.service.NotificationService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController Tests")
class NotificationControllerTest {

    @Mock NotificationService notificationService;
    @InjectMocks NotificationController notificationController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    private UserPrincipal patientPrincipal;
    private UserPrincipal adminPrincipal;
    private NotificationResponse sampleNotification;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
            .standaloneSetup(notificationController)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        patientPrincipal = new UserPrincipal(10L, "patient@example.com", "PATIENT");
        adminPrincipal   = new UserPrincipal(99L, "admin@example.com",   "ADMIN");

        sampleNotification = NotificationResponse.builder()
            .id(1L).recipientId(10L)
            .recipientEmail("patient@example.com")
            .type(NotificationType.EMAIL)
            .subject("Appointment Confirmed")
            .body("Your appointment has been booked.")
            .status(NotificationStatus.SENT)
            .eventType("appointment.booked")
            .sentAt(Instant.now())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    // ── GET /notifications/my ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/notifications/my")
    class GetMyNotificationsTests {

        @Test
        @DisplayName("Should return 200 with user's notifications")
        void getMyNotifications_success() throws Exception {
            authenticateAs(patientPrincipal);

            PagedResponse<NotificationResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(sampleNotification)));

            when(notificationService.getNotifications(10L, 0, 20)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/notifications/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notifications retrieved"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].recipientId").value(10))
                .andExpect(jsonPath("$.data.content[0].subject").value("Appointment Confirmed"))
                .andExpect(jsonPath("$.data.content[0].status").value("SENT"));

            verify(notificationService).getNotifications(10L, 0, 20);
        }

        @Test
        @DisplayName("Should return 200 with empty list when no notifications exist")
        void getMyNotifications_empty() throws Exception {
            authenticateAs(patientPrincipal);

            PagedResponse<NotificationResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of()));

            when(notificationService.getNotifications(10L, 0, 20)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/notifications/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content").isEmpty());
        }

        @Test
        @DisplayName("Should respect page and size query parameters")
        void getMyNotifications_customPagination() throws Exception {
            authenticateAs(patientPrincipal);

            PagedResponse<NotificationResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of()));

            when(notificationService.getNotifications(10L, 1, 5)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/notifications/my")
                    .param("page", "1")
                    .param("size", "5"))
                .andExpect(status().isOk());

            verify(notificationService).getNotifications(10L, 1, 5);
        }
    }

    // ── GET /notifications/my/unread-count ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/notifications/my/unread-count")
    class GetUnreadCountTests {

        @Test
        @DisplayName("Should return 200 with the unread count")
        void getUnreadCount_success() throws Exception {
            authenticateAs(patientPrincipal);

            when(notificationService.countUnread(10L)).thenReturn(3L);

            mockMvc.perform(get("/api/v1/notifications/my/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(3));

            verify(notificationService).countUnread(10L);
        }

        @Test
        @DisplayName("Should return 0 when all notifications are read")
        void getUnreadCount_zero() throws Exception {
            authenticateAs(patientPrincipal);

            when(notificationService.countUnread(10L)).thenReturn(0L);

            mockMvc.perform(get("/api/v1/notifications/my/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
        }
    }

    // ── GET /notifications/user/{userId} (admin) ───────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/notifications/user/{userId}")
    class GetForUserTests {

        @Test
        @DisplayName("Should return 200 with notifications for the specified user")
        void getForUser_success() throws Exception {
            authenticateAs(adminPrincipal);

            PagedResponse<NotificationResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of(sampleNotification)));

            when(notificationService.getNotifications(10L, 0, 20)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/notifications/user/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].recipientId").value(10));

            verify(notificationService).getNotifications(10L, 0, 20);
        }

        @Test
        @DisplayName("Should return 200 with empty list when user has no notifications")
        void getForUser_empty() throws Exception {
            authenticateAs(adminPrincipal);

            PagedResponse<NotificationResponse> paged = PagedResponse.from(
                new PageImpl<>(List.of()));

            when(notificationService.getNotifications(99L, 0, 20)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/notifications/user/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }
}
