package com.hms.notification.service;

import com.hms.notification.dto.response.NotificationResponse;
import com.hms.notification.dto.response.PagedResponse;
import com.hms.notification.entity.Notification;
import com.hms.notification.enums.NotificationStatus;
import com.hms.notification.enums.NotificationType;
import com.hms.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService           emailService;

    @Transactional
    public void sendEmail(Long recipientId, String recipientEmail, String eventType, String subject, String body) {
        Notification notification = Notification.builder()
                .recipientId(recipientId)
                .recipientEmail(recipientEmail)
                .channel(NotificationType.EMAIL)
                .type(eventType)
                .subject(subject)
                .body(body)
                .build();

        try {
            emailService.send(recipientEmail, subject, body);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            log.info("Email notification sent for event={} to={}", eventType, recipientEmail);
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
            log.error("Failed to send email notification for event={}: {}", eventType, e.getMessage());
        }

        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getNotifications(Long recipientId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.from(
                notificationRepository.findByRecipientId(recipientId, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public long countUnread(Long recipientId) {
        return notificationRepository.countByRecipientIdAndStatus(recipientId, NotificationStatus.PENDING);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipientId())
                .recipientEmail(n.getRecipientEmail())
                .type(n.getChannel())
                .subject(n.getSubject())
                .body(n.getBody())
                .status(n.getStatus())
                .eventType(n.getType())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
