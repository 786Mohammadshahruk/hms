package com.hms.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.notification.enums.NotificationStatus;
import com.hms.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {
    private final Long               id;
    private final Long               recipientId;
    private final String             recipientEmail;
    private final NotificationType   type;    // channel (EMAIL/SMS/PUSH/IN_APP)
    private final String             subject;
    private final String             body;
    private final NotificationStatus status;
    private final String             eventType; // free-form event name
    private final Instant            sentAt;
    private final Instant            createdAt;
}
