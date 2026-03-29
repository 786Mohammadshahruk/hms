package com.hms.medical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Medical Records Service — prescriptions, medicines, and medical tests.
 * Swagger UI: http://localhost:8083/swagger-ui.html
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@EnableScheduling
public class MedicalRecordsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicalRecordsServiceApplication.class, args);
    }
}
