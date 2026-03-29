package com.hms.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsService {

    /**
     * Dummy SMS sender — logs to console instead of calling Twilio or any real SMS gateway.
     */
    public void send(String phoneNumber, String message) {
        log.info("=== [DUMMY SMS] ===");
        log.info("To:      {}", phoneNumber);
        log.info("Message: {}", message);
        log.info("==================");
    }
}
