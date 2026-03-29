package com.hms.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Application-level configuration:
 * JPA auditing, async thread pool, scheduling, and OpenAPI/Swagger setup.
 */
@Configuration
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class AppConfig {

    // ── OpenAPI / Swagger ──────────────────────────────────────────────────────

    @Bean
    public OpenAPI userServiceOpenAPI() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
            .info(new Info()
                .title("HMS User Service API")
                .description("Handles user registration, authentication, JWT, and profile management")
                .version("1.0.0")
                .contact(new Contact().name("HMS Team").email("dev@hms.local")))
            .addSecurityItem(new SecurityRequirement().addList(schemeName))
            .components(new Components()
                .addSecuritySchemes(schemeName, new SecurityScheme()
                    .name(schemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }

    // ── Async Executor ─────────────────────────────────────────────────────────

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("hms-async-");
        executor.initialize();
        return executor;
    }
}
