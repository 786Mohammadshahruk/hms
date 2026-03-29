package com.hms.appointment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        jwtTokenProvider.validateAndExtract(token).ifPresent(claims -> {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String email  = claims.getSubject();
                String role   = claims.get("role", String.class);
                Object userIdObj = claims.get("userId");
                Long userId = null;
                if (userIdObj instanceof Integer) userId = ((Integer) userIdObj).longValue();
                else if (userIdObj instanceof Long) userId = (Long) userIdObj;
                else if (userIdObj != null) userId = Long.parseLong(userIdObj.toString());

                if (email != null && role != null && userId != null) {
                    UserPrincipal principal = new UserPrincipal(userId, email, role);
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Authenticated userId={} role={} for path={}", userId, role, request.getRequestURI());
                }
            }
        });

        filterChain.doFilter(request, response);
    }
}
