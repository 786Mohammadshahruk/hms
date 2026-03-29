package com.hms.billing.security;

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
