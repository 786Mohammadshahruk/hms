package com.hms.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Instant;

/**
 * Reactive JWT authentication filter for Spring Cloud Gateway.
 *
 * <p>Validates Bearer tokens using JJWT 0.12.x and the same hex-encoded
 * 256-bit key used by the user-service for token generation.
 * On success, forwards X-User-Id, X-User-Email, and X-User-Role headers
 * to downstream services so they don't need to re-parse the token.
 */
@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final SecretKey signingKey;

    public AuthenticationFilter(@Value("${jwt.secret}") String secretHex) {
        super(Config.class);
        this.signingKey = Keys.hmacShaKeyFor(hexToBytes(secretHex));
    }

    @Override
    public GatewayFilter apply(Config config) {
        return this::doFilter;
    }

    private Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return onError(exchange, "Authorization header is missing", HttpStatus.UNAUTHORIZED);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, "Invalid authorization header format", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            // userId is stored as Integer in the JWT claims map
            Object userIdObj = claims.get("userId");
            String userId = userIdObj != null ? userIdObj.toString() : "";
            String role   = claims.get("role", String.class);
            String email  = claims.getSubject();

            ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id",    userId)
                .header("X-User-Email", email  != null ? email : "")
                .header("X-User-Role",  role   != null ? role  : "")
                .build();

            log.debug("JWT validated — userId={}, role={}, path={}",
                userId, role, request.getPath());

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("JWT expired for path {}: {}", request.getPath(), e.getMessage());
            return onError(exchange, "Token has expired. Please login again.", HttpStatus.UNAUTHORIZED);
        } catch (JwtException e) {
            log.warn("JWT invalid for path {}: {}", request.getPath(), e.getMessage());
            return onError(exchange, "Invalid or malformed token", HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            log.error("Unexpected auth error for path {}: {}", request.getPath(), e.getMessage());
            return onError(exchange, "Authentication error", HttpStatus.UNAUTHORIZED);
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
            "{\"success\":false,\"message\":\"%s\",\"timestamp\":\"%s\"}",
            message, Instant.now());
        org.springframework.core.io.buffer.DataBuffer buffer =
            response.bufferFactory().wrap(body.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    private static byte[] hexToBytes(String hex) {
        int    len  = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /** No config params needed. */
    public static class Config { }
}