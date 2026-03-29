package com.hms.user.security;

import com.hms.user.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * JWT utility — sole authority for token generation in the HMS system.
 * The API Gateway only validates; this service creates tokens.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long      accessTokenExpirationMs;
    private final long      refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretHex,
            @Value("${jwt.expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshTokenExpirationMs) {

        this.signingKey               = Keys.hmacShaKeyFor(hexToBytes(secretHex));
        this.accessTokenExpirationMs  = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    // ── Token Generation ───────────────────────────────────────────────────────

    /**
     * Generates a signed access token embedding userId, email and role as claims.
     */
    public String generateAccessToken(Long userId, String email, Role role) {
        return Jwts.builder()
            .subject(email)
            .claims(Map.of(
                "userId", userId,
                "role",   role.name()
            ))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
            .signWith(signingKey)
            .compact();
    }

    /**
     * Generates a long-lived opaque refresh token (also a JWT for consistency).
     */
    public String generateRefreshToken(Long userId, String email) {
        return Jwts.builder()
            .subject(email)
            .claims(Map.of("userId", userId, "type", "REFRESH"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
            .signWith(signingKey)
            .compact();
    }

    // ── Token Validation & Extraction ─────────────────────────────────────────

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
        return extractClaim(token, c -> c.get("userId", Long.class));
    }

    public boolean isTokenValid(String token) {
        return validateAndExtract(token).isPresent();
    }

    public long getRefreshExpirationMs() {
        return refreshTokenExpirationMs;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static byte[] hexToBytes(String hex) {
        int    len  = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
