package com.hms.appointment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Appointment Service — real-time booking, rescheduling, cancellation,
 * doctor schedule management, and Redis-cached slot availability.
 * Swagger UI: http://localhost:8082/swagger-ui.html
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@EnableAsync
@EnableScheduling
public class AppointmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentServiceApplication.class, args);
    }
}
