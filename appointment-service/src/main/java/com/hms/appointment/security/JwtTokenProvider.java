package com.hms.appointment.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Optional;
import java.util.function.Function;

/**
 * JWT token validator for the Appointment Service.
 * Does NOT generate tokens — that is the user-service's sole responsibility.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;

    public JwtTokenProvider(@Value("${jwt.secret}") String secretHex) {
        this.signingKey = Keys.hmacShaKeyFor(hexToBytes(secretHex));
    }

    public Optional<Claims> validateAndExtract(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Optional.of(claims);
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
            return Optional.empty();
        } catch (JwtException e) {
            log.warn("JWT invalid: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return validateAndExtract(token)
            .map(resolver)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, c -> {
            Object id = c.get("userId");
            if (id instanceof Integer) return ((Integer) id).longValue();
            if (id instanceof Long) return (Long) id;
            return Long.parseLong(id.toString());
        });
    }

    public boolean isTokenValid(String token) {
        return validateAndExtract(token).isPresent();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}