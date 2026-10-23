package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.entities.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(
            @Value("${app.security.jwt.secret}") String encodedSecret,
            @Value("${app.security.jwt.expiration}") Duration expiration) {
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be valid Base64", exception);
        }
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must decode to at least 32 bytes");
        }
        if (expiration.isNegative() || expiration.isZero()) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expiration = expiration;
    }

    public String generateToken(UUID userId, String email, Role role) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        Date issuedAt = new Date();
        Date expiresAt = Date.from(issuedAt.toInstant().plus(expiration));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (claims.getExpiration() == null) {
            throw new MalformedJwtException("JWT is missing expiration");
        }

        String subject = claims.getSubject();
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new MalformedJwtException("JWT subject must be a UUID", exception);
        }
        if (!userId.toString().equalsIgnoreCase(subject)) {
            throw new MalformedJwtException("JWT subject must be a UUID");
        }

        String email = claims.get("email", String.class);
        String roleValue = claims.get("role", String.class);
        if (email == null || email.isBlank() || roleValue == null) {
            throw new MalformedJwtException("JWT is missing required claims");
        }

        try {
            return new AuthenticatedUser(userId, email, Role.valueOf(roleValue));
        } catch (IllegalArgumentException exception) {
            throw new MalformedJwtException("JWT contains an invalid role", exception);
        }
    }
}
