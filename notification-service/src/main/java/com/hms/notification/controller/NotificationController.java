package com.hms.notification.controller;

import com.hms.notification.dto.response.ApiResponse;
import com.hms.notification.dto.response.NotificationResponse;
import com.hms.notification.dto.response.PagedResponse;
import com.hms.notification.security.UserPrincipal;
import com.hms.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification history APIs")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my notifications")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved",
                notificationService.getNotifications(principal.getUserId(), page, size)));
    }

    @GetMapping("/my/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Unread count",
                notificationService.countUnread(principal.getUserId())));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get notifications for a user (admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> getForUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved",
                notificationService.getNotifications(userId, page, size)));
    }
}
