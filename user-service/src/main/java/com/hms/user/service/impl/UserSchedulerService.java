package com.hms.user.service.impl;

import com.hms.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled jobs for the User Service.
 * Runs housekeeping tasks such as purging expired refresh tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSchedulerService {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Deletes expired refresh tokens every day at 02:00.
     * Keeps the refresh_tokens table lean.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        log.info("Scheduler: purging expired refresh tokens...");
        refreshTokenRepository.deleteAllExpiredBefore(Instant.now());
        log.info("Scheduler: expired refresh tokens purged.");
    }
}
