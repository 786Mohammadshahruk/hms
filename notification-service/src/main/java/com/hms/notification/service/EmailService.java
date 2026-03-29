package com.hms.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    /**
     * Dummy email sender — logs to console instead of sending real emails.
     */
    public void send(String to, String subject, String body) {
        log.info("=== [DUMMY EMAIL] ===");
        log.info("To:      {}", to);
        log.info("Subject: {}", subject);
        log.info("Body:    {}", body);
        log.info("====================");
    }
}
