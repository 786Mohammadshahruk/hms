package com.hms.medical.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final String SECRET_HEADER     = "X-Internal-Secret";
    private static final String USER_ID_HEADER    = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLE_HEADER  = "X-User-Role";

    @Value("${internal.secret}")
    private String internalSecret;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String secret = request.getHeader(SECRET_HEADER);

        if (!internalSecret.equals(secret)) {
            log.warn("Rejected direct service call from {} — missing or invalid X-Internal-Secret",
                    request.getRemoteAddr());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Access via API Gateway only\"}");
            return;
        }

        String userIdStr = request.getHeader(USER_ID_HEADER);
        String email     = request.getHeader(USER_EMAIL_HEADER);
        String role      = request.getHeader(USER_ROLE_HEADER);

        if (userIdStr != null && email != null && role != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Long userId = Long.parseLong(userIdStr);
                UserPrincipal principal = new UserPrincipal(userId, email, role);
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Internal auth: userId={} role={} path={}", userId, role, request.getRequestURI());
            } catch (NumberFormatException e) {
                log.warn("Invalid X-User-Id header value: {}", userIdStr);
            }
        }

        filterChain.doFilter(request, response);
    }
}
