package com.hms.user.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates MDC (Mapped Diagnostic Context) for every incoming HTTP request.
 *
 * Fields set:
 *   requestId  — short UUID, also echoed back as X-Request-Id response header
 *   userId     — from X-User-Id header injected by API Gateway
 *   userEmail  — from X-User-Email header injected by API Gateway
 *   userRole   — from X-User-Role header injected by API Gateway
 *   method     — HTTP method (GET, POST, ...)
 *   path       — request URI
 *
 * MDC is always cleared in the finally block so values never leak
 * across requests on the same thread.
 */
@Component
@Order(1)
public class MdcLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            MDC.put("requestId", requestId);
            MDC.put("userId",    header(request, "X-User-Id"));
            MDC.put("userEmail", header(request, "X-User-Email"));
            MDC.put("userRole",  header(request, "X-User-Role"));
            MDC.put("method",    request.getMethod());
            MDC.put("path",      request.getRequestURI());

            response.setHeader("X-Request-Id", requestId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String header(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return v != null && !v.isBlank() ? v : "-";
    }
}
